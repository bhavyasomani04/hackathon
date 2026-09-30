# Architecture Decision Records — ZipRun AI Reassignment Engine

Each entry follows: **Context → Options considered → Decision → Tradeoffs accepted**. Written in the moment, as each decision was made — not reconstructed at the end.

---

## ADR-1 — Where does routing logic live?

### Context
The system needs a place for the logic that picks the best agent for an order. That logic is called from two places today (an HTTP endpoint for on-demand suggestions and, from T-4 onward, an async event handler after an agent goes OFFLINE) and will grow in sprint 2 (`ZoneAffinityStrategy`) and T-3 (AI strategy). A naïve controller-embedded implementation would leak business logic into HTTP handlers and couple two very different call paths.

### Options considered
**(a) Inline in the controller** — quickest, but couples routing to HTTP and can't be reused from the async listener without duplication.
**(b) A single `OrderService` method that grows organically** — routing, event publishing, and persistence all in one class. Works short-term but is the "God service" smell the brief explicitly warns about.
**(c) Dedicated `routing` package with a strategy interface + an orchestrating `RoutingService` in the service layer** — separates the *policy* (which strategy, and how it ranks) from the *workflow* (loading candidates, calling the strategy, persisting the suggestion, flipping the order to `REASSIGNMENT_PENDING`).

### Decision
Chose (c). Interfaces and strategy implementations live in [`com.example.demo.routing`](Hackathon_backend/src/main/java/com/example/demo/routing) (`RoutingStrategy`, `RoutingContext`, `Recommendation`, `RuleBasedStrategy`, `RoutingStrategyRegistry`). The `RoutingService` in [`com.example.demo.service`](Hackathon_backend/src/main/java/com/example/demo/service/RoutingService.java) is the single orchestrator both call sites use — its `suggestFor(order, triggerReason)` method is called by the HTTP endpoint today and will be called by the T-4 event listener tomorrow. No routing logic lives in controllers; controllers stay thin (validation + delegation).

### Tradeoffs accepted
More files up-front than a monolithic service. A junior reader has to follow one hop (controller → `RoutingService`) to see the workflow and another (`RoutingService` → strategy) to see the ranking. Accepted this because the separation is what makes strategies swappable and the async caller free — those are structural requirements, not niceties.

---

## ADR-2 — How does runtime strategy switchability work?

### Context
The routing engine supports multiple strategies (rule-based today; AI in T-3; `ZoneAffinityStrategy` in sprint 2). The active strategy must be switchable **at runtime — no code change, no restart**. The same routing contract is called from an HTTP endpoint and from an async event handler in the agentic loop, so both callers must see the same active strategy without extra wiring.

### Options considered
**(a) Spring `@Qualifier` + config property** — straightforward, but requires a restart to switch and doesn't scale cleanly to dynamic registration.
**(b) Auto-wired `Map<String, RoutingStrategy>` behind a registry** — Spring populates the map by bean name; the registry reads the active strategy name from `Environment` on every call. Adding a new strategy = new `@Component` implementing the interface. Nothing else changes.
**(c) Manual factory with a `switch` statement** — explicit but requires modifying the factory every time a strategy is added.

### Decision
Chose (b). See [`RoutingStrategyRegistry`](Hackathon_backend/src/main/java/com/example/demo/routing/RoutingStrategyRegistry.java). Each strategy exposes its own `name()` (e.g. `"rule-based"`) and is picked up as a Spring `@Component`. The registry's constructor collects all `RoutingStrategy` beans into a `Map<String, RoutingStrategy>` keyed by that name. `active()` reads `routing.strategy` from `Environment` **on every invocation** — deliberately not cached — so changing the property (env var, config server, actuator refresh) takes effect on the next call without a restart. A `@PostConstruct` validator fails fast at startup if the configured name doesn't match any registered strategy. Both `RoutingService` (HTTP path) and — from T-4 — the async event listener resolve the strategy through this same registry, so they always agree.

### Tradeoffs accepted
The bean-map approach is slightly less explicit than a factory — a reader has to know that Spring populates the map by bean name and that the registry consults it dynamically. Reading `Environment` per call adds a tiny lookup cost (negligible against a DB write). We lose compile-time guarantees that the configured strategy name exists, which is why the startup validator is important. If two strategies ever registered under the same name, `Collectors.toMap` would throw at boot — a merge function would silently pick one, which is worse; keeping the throw is intentional.

---

## ADR-3 — How does the system stay resilient when the LLM is unavailable?

### Context
The AI routing strategy calls an external LLM (Gemini/Groq/Ollama). LLM calls fail in several distinct ways: timeouts, HTTP errors, quota exhaustion, malformed JSON, missing fields, and hallucinated agent identifiers that don't exist in our roster. The reassignment flow must stay healthy regardless — especially in the async re-plan path where a silent drop is strictly worse than a rule-based recommendation.

### Options considered
**(a) Let the failure propagate and return an error to the caller** — honest, but breaks the async re-plan loop entirely on any LLM hiccup. Ops sees nothing and orders sit stranded.
**(b) Retry with exponential backoff, then propagate** — improves transient error tolerance but doubles latency and still fails hard on structured errors (quota, hallucination).
**(c) Explicit per-failure-mode handling inside the strategy, with a rule-based fallback that still produces a suggestion** — the reassignment always completes; ops sees the AI's answer when it worked and a labelled fallback when it didn't.

### Decision
Chose (c). See [`AiStrategy`](Hackathon_backend/src/main/java/com/example/demo/routing/AiStrategy.java). Three failure boundaries, each caught, logged at WARN with the failure class, and delegated to a directly-injected [`RuleBasedStrategy`](Hackathon_backend/src/main/java/com/example/demo/routing/RuleBasedStrategy.java):
1. **Transport / HTTP** — `LLMGateway.callLLM` throws → catch, fallback.
2. **Response shape** — [`LLMResponseParser`](Hackathon_backend/src/main/java/com/example/demo/ai/LLMResponseParser.java) throws on empty, malformed, or missing-field responses → catch, fallback. The parser is deliberately tolerant of markdown fences and prose because LLMs are.
3. **Hallucinated agent id** — after parsing, we look up the returned `agentId` in the candidate list; if it's not there, treat as failure and fall back. This is the mode most likely to look successful and cause the worst downstream damage, so it gets the same fallback path as a hard error.

Fallback recommendations are prefixed `[fallback: <reason>]` in the reasoning string so the same field ops reads in the UI carries the diagnostic. Confidence is clamped to `[0,1]` at parse time — an LLM returning `1.7` isn't a failure worth falling back for, just a value worth clamping.

### Tradeoffs accepted
No retry logic — a transient blip forces a rule-based answer instead of the AI's. Accepted because retries during an async re-plan storm compound load exactly when the system is stressed; a rule-based suggestion beats a delayed better one. The rule-based fallback runs in the same request thread; if it too fails (e.g. no candidates at all), the exception propagates and `RoutingService` throws `NoAvailableAgentsException` — the correct end state, but ops sees the failure only in logs on the async path. `AsyncConfig`'s bounded pool + per-order try/catch in the listener limits blast radius.

---

---

## ADR-4 — How is the agentic loop triggered and kept off the request path?

### Context
`PATCH /agents/{id}/status` must return quickly — the ops UI is waiting on the response. Re-planning affected orders when an agent goes OFFLINE can involve N LLM calls and N DB writes, so it cannot happen on the request thread. The mechanism must also handle async failures without either dropping the re-plan silently or wedging the endpoint, and must not read the agent's status before the transaction that set it OFFLINE has committed.

### Options considered
**(a) Inline re-plan in the PATCH handler** — simplest, but blocks the response and couples HTTP timeouts to LLM latency. Rejected outright.
**(b) Scheduled poller for OFFLINE agents** — brief explicitly calls this out as wrong: the loop should fire because *something changed*, not because a timer ticked.
**(c) Standard `@Async @EventListener`** — decouples publish from consume, but the listener fires when `publishEvent` is called (before the transaction commits). On a fast dispatcher thread it can read the agent as still AVAILABLE, or the freshly-created events row can be visible before the status change.
**(d) `@Async @TransactionalEventListener(phase = AFTER_COMMIT)`** — same benefits as (c), but the listener is queued to run after the publishing transaction commits, so it sees the world in the state the caller intended.
**(e) A dedicated `ExecutorService` submitted from the service** — works, but leaks async concerns into the service and reinvents what Spring gives us.

### Decision
Chose (d). See [`AgentOfflineListener`](Hackathon_backend/src/main/java/com/example/demo/events/AgentOfflineListener.java). Two annotations, together, deliver both properties:
- **`@TransactionalEventListener(phase = AFTER_COMMIT)`** — the listener only fires after `AgentService.updateStatus` commits. No stale reads, no half-visible state.
- **`@Async("replanExecutor")`** — dispatches to a bounded thread pool defined in [`AsyncConfig`](Hackathon_backend/src/main/java/com/example/demo/config/AsyncConfig.java) (core 2, max 4, queue 50). The PATCH handler returns immediately after the commit.

The listener body: find orders assigned to the offline agent that aren't `REASSIGNED`/`DELIVERED`, and for each one, check the idempotency guard `(orderId, PENDING, AGENT_OFFLINE)` before delegating to `RoutingService.suggestFor(order, AGENT_OFFLINE)`. Each order runs in its own try/catch — a failure on one doesn't sink the batch. Failures are logged at WARN with the failure class; the AI strategy's own fallback (ADR-3) ensures a rule-based suggestion still lands even if the LLM is down.

### Tradeoffs accepted
`AFTER_COMMIT` means the listener isn't part of any transaction that could still roll back — accepted, because rolling back a manual status change based on an async re-plan failure would be surprising and worse than the current behaviour (endpoint succeeds; async failure is logged and visible in ops later via the missing suggestion, not a rolled-back status). The bounded pool caps concurrent re-plans at 4; a huge simultaneous outage (many agents OFFLINE at once) queues rather than parallelising — a deliberate load-shedding choice, not a bug. Idempotency uses a repository read + insert (not a DB unique constraint); under extremely high concurrency two threads could race between the read and write for the same order. For sprint 1 the AFTER_COMMIT ordering + the bounded pool make this vanishingly unlikely; a unique index on `(order_id, status, trigger_reason)` where `status=PENDING` is the sprint-2 hardening.

---

## ADR-5 — What did you design to extend, and what did you save for later?

### Context
Sprint 2 adds `ZoneAffinityStrategy`, `Order.pickupZone`/`dropoffZone`, `Order.weightClass`, `Agent.maxCapacity`. Sprint 3 adds SLA-driven re-plans (a different trigger for the same loop). The brief judges the extensibility story by whether adding these means "implementing the contract and registering it — nothing more."

### Decision — the seams
1. **Adding a new strategy is a pure `@Component` addition.** [`RoutingStrategyRegistry`](Hackathon_backend/src/main/java/com/example/demo/routing/RoutingStrategyRegistry.java) auto-discovers any bean implementing [`RoutingStrategy`](Hackathon_backend/src/main/java/com/example/demo/routing/RoutingStrategy.java). Sprint 2's `ZoneAffinityStrategy` will drop in as one new file. Zero existing files change. Flipping to it is `routing.strategy=zone-affinity` — no restart.
2. **Adding new inputs to the strategy interface won't break existing strategies.** Arguments are wrapped in a [`RoutingContext`](Hackathon_backend/src/main/java/com/example/demo/routing/RoutingContext.java) record. Sprint 2 adds `pickupZone`; existing strategies simply ignore the new field. This is the reason the interface takes a context object rather than positional arguments.
3. **A new trigger for the agentic loop plugs into the same event mechanism.** Sprint 3's SLA-breach loop can publish its own event (e.g. `SlaBreachImminentEvent`) that a sibling `@Async @EventListener` handles by calling the same `RoutingService.suggestFor(order, TriggerReason.SLA_BREACH)`. `TriggerReason` and `RoutingContext.triggerReason` are already in place for this — sprint 3 adds an enum value, not a new code path.

### Deliberate exclusions
- **Full dispatch board (sprint-3 territory)** — deferred because the agentic re-plan loop is a correctness requirement and the board is a visibility enhancement. If I shipped the board but the loop didn't idempotently avoid duplicate suggestions, ops would see a mess.
- **Zone/weight/capacity fields on `Order` and `Agent`** — chose not to add nullable placeholder columns yet. The `RoutingContext` wrapper means the strategy signature won't need to change when they're introduced, so a sprint-2 migration is the only cost. A wrong-guess placeholder now (wrong nullability, wrong type, wrong column name) would cost more than the migration.
- **Auto-accept on high AI confidence** — the brief is explicit that the human checkpoint is the point of the design. Removing it needs a separate ADR when we have production data to support a threshold.

### Tradeoffs accepted
Points (1)–(3) above trade a bit of indirection for these guarantees: readers must understand three collaborating pieces (interface, registry, service) instead of one. Accepted because the alternative — refactoring existing strategies when sprint 2 lands — is the exact anti-pattern this design exists to prevent.

---

_Sections marked "pending" will be filled in as T-3 and T-4 are built, per the brief's guidance to write ADRs in the moment rather than at the end._
