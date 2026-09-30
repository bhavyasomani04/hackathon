package com.example.demo.events;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.SuggestionStatus;
import com.example.demo.entity.TriggerReason;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ReassignmentSuggestionRepository;
import com.example.demo.service.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * T-4 — Agentic re-planning loop.
 *
 * <p><b>Trigger:</b> {@link AgentWentOfflineEvent}, published by
 * {@code AgentService.updateStatus} whenever an agent transitions to OFFLINE.
 *
 * <p><b>Kept off the request path via two mechanisms working together:</b>
 * <ol>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} — the
 *       listener fires only after the {@code AgentService} transaction commits.
 *       This prevents a race where the async thread reads the agent as still
 *       AVAILABLE.</li>
 *   <li>{@code @Async("replanExecutor")} — dispatches to a bounded pool
 *       (see {@code AsyncConfig}) so the PATCH endpoint returns immediately.</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> per-order, we skip creating a new suggestion if a
 * {@code PENDING} + {@code AGENT_OFFLINE} suggestion already exists. Same
 * agent flipping OFFLINE twice does not produce duplicates.
 *
 * <p><b>Failure isolation:</b> each order is re-planned in its own try/catch.
 * A single failure (e.g. an order whose current agent is somehow already null)
 * does not stop the rest. The AI strategy's own fallback ensures a rule-based
 * suggestion is produced even if the LLM is down.
 */
@Component
public class AgentOfflineListener {

    private static final Logger log = LoggerFactory.getLogger(AgentOfflineListener.class);

    private final OrderRepository orderRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final RoutingService routingService;

    public AgentOfflineListener(OrderRepository orderRepository,
                                ReassignmentSuggestionRepository suggestionRepository,
                                RoutingService routingService) {
        this.orderRepository = orderRepository;
        this.suggestionRepository = suggestionRepository;
        this.routingService = routingService;
    }

    @Async("replanExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAgentWentOffline(AgentWentOfflineEvent event) {
        log.info("Agentic loop fired for agent={} at={}", event.agentId(), event.at());

        List<Order> assigned = orderRepository.findByAssignedAgentId(event.agentId());
        List<Order> stranded = assigned.stream()
                .filter(o -> o.getStatus() != OrderStatus.REASSIGNED
                        && o.getStatus() != OrderStatus.DELIVERED)
                .toList();

        if (stranded.isEmpty()) {
            log.info("Agent {} had no stranded orders; nothing to re-plan.", event.agentId());
            return;
        }

        int replanned = 0;
        int skipped = 0;
        int failed = 0;

        for (Order order : stranded) {
            try {
                boolean duplicate = !suggestionRepository
                        .findByOrderIdAndStatusAndTriggerReason(
                                order.getId(),
                                SuggestionStatus.PENDING,
                                TriggerReason.AGENT_OFFLINE)
                        .isEmpty();

                if (duplicate) {
                    log.info("Skipping order {} — PENDING AGENT_OFFLINE suggestion already exists.",
                            order.getId());
                    skipped++;
                    continue;
                }

                routingService.suggestFor(order, TriggerReason.AGENT_OFFLINE);
                replanned++;
            } catch (Exception e) {
                // Explicit surfacing — a silent drop would be worse than a bad log line.
                log.warn("Re-plan failed for order {} (agent {}): {}: {}",
                        order.getId(), event.agentId(),
                        e.getClass().getSimpleName(), e.getMessage());
                failed++;
            }
        }

        log.info("Agentic loop done for agent={}: replanned={}, skipped={}, failed={}",
                event.agentId(), replanned, skipped, failed);
    }
}
