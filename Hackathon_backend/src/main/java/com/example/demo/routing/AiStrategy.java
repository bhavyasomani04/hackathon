package com.example.demo.routing;

import com.example.demo.ai.LLMGateway;
import com.example.demo.ai.LLMResponse;
import com.example.demo.ai.LLMResponseParser;
import com.example.demo.ai.PromptBuilder;
import com.example.demo.entity.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * LLM-backed routing strategy.
 *
 * <p>Contract preserved: implements {@link RoutingStrategy}, returns
 * a ranked list of {@link Recommendation}s. The LLM returns exactly one
 * choice — we return it as a single-element list — unless anything
 * goes wrong, in which case we delegate to {@link RuleBasedStrategy}
 * (per the brief: "a silent drop is worse than a rule-based recommendation").
 *
 * <p>Failure modes handled explicitly:
 * <ul>
 *   <li>Timeout / HTTP error inside {@link LLMGateway} → catch, log, fallback.</li>
 *   <li>Malformed JSON / missing fields → parser throws, catch, log, fallback.</li>
 *   <li>Hallucinated agent id not in candidate list → treat as failure, fallback.</li>
 *   <li>Empty candidate list → return empty list; {@code RoutingService} handles it.</li>
 * </ul>
 */
@Component
public class AiStrategy implements RoutingStrategy {

    public static final String NAME = "ai";
    private static final Logger log = LoggerFactory.getLogger(AiStrategy.class);

    private final LLMGateway gateway;
    private final PromptBuilder promptBuilder;
    private final LLMResponseParser parser;
    private final RuleBasedStrategy fallback;

    public AiStrategy(LLMGateway gateway,
                      PromptBuilder promptBuilder,
                      LLMResponseParser parser,
                      RuleBasedStrategy fallback) {
        this.gateway = gateway;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.fallback = fallback;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Recommendation> recommend(RoutingContext ctx) {
        if (ctx.candidates() == null || ctx.candidates().isEmpty()) {
            return List.of();
        }

        String prompt = promptBuilder.build(ctx);
        String raw;
        try {
            raw = gateway.callLLM(prompt);
        } catch (Exception e) {
            log.warn("AI strategy: LLM call failed for order={} trigger={} — falling back to rule-based ({}: {})",
                    ctx.order().getId(), ctx.triggerReason(),
                    e.getClass().getSimpleName(), e.getMessage());
            return fallbackWithNote(ctx, "LLM call failed (" + e.getClass().getSimpleName() + ")");
        }

        LLMResponse parsed;
        try {
            parsed = parser.parse(raw);
        } catch (Exception e) {
            log.warn("AI strategy: LLM response unparseable for order={} — falling back ({})",
                    ctx.order().getId(), e.getMessage());
            return fallbackWithNote(ctx, "LLM response unparseable");
        }

        Optional<Agent> match = ctx.candidates().stream()
                .filter(a -> a.getId().equals(parsed.agentId()))
                .findFirst();
        if (match.isEmpty()) {
            log.warn("AI strategy: LLM returned agentId='{}' not in candidate list for order={} — falling back",
                    parsed.agentId(), ctx.order().getId());
            return fallbackWithNote(ctx, "AI returned unknown agent id '" + parsed.agentId() + "'");
        }

        return List.of(new Recommendation(match.get(), parsed.confidence(), parsed.reasoning()));
    }

    /**
     * Runs the rule-based fallback and prefixes its reasoning so ops can see
     * why the AI didn't answer. Keeps the suggestion flowing rather than
     * dropping it — as required for the async re-plan path.
     */
    private List<Recommendation> fallbackWithNote(RoutingContext ctx, String note) {
        List<Recommendation> rb = fallback.recommend(ctx);
        return rb.stream()
                .map(r -> new Recommendation(r.agent(), r.confidence(),
                        "[fallback: " + note + "] " + r.reasoning()))
                .toList();
    }
}
