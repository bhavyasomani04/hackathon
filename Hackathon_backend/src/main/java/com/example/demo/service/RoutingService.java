package com.example.demo.service;

import com.example.demo.api.dto.SuggestionResponse;
import com.example.demo.entity.Agent;
import com.example.demo.entity.AgentStatus;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.ReassignmentSuggestion;
import com.example.demo.entity.TriggerReason;
import com.example.demo.exception.NoAvailableAgentsException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.repository.AgentRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ReassignmentSuggestionRepository;
import com.example.demo.routing.Recommendation;
import com.example.demo.routing.RoutingContext;
import com.example.demo.routing.RoutingStrategy;
import com.example.demo.routing.RoutingStrategyRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The one entry point both callers use: HTTP {@code POST /orders/{id}/suggest}
 * and (later) the T-4 async listener on {@code AgentWentOfflineEvent}.
 *
 * <p>Kept intentionally strategy-agnostic — it prepares the candidate list,
 * asks the active strategy for recommendations, and persists the top one.
 */
@Service
public class RoutingService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final RoutingStrategyRegistry registry;

    public RoutingService(OrderRepository orderRepository,
                          AgentRepository agentRepository,
                          ReassignmentSuggestionRepository suggestionRepository,
                          RoutingStrategyRegistry registry) {
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
        this.suggestionRepository = suggestionRepository;
        this.registry = registry;
    }

    @Transactional
    public SuggestionResponse suggestForOrderId(String orderId, TriggerReason triggerReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return suggestFor(order, triggerReason);
    }

    @Transactional
    public SuggestionResponse suggestFor(Order order, TriggerReason triggerReason) {
        List<Agent> candidates = pickCandidates(order);
        if (candidates.isEmpty()) {
            throw new NoAvailableAgentsException(order.getId());
        }

        RoutingStrategy strategy = registry.active();
        List<Recommendation> recs = strategy.recommend(
                new RoutingContext(order, candidates, triggerReason));
        if (recs.isEmpty()) {
            throw new NoAvailableAgentsException(order.getId());
        }

        Recommendation top = recs.get(0);
        ReassignmentSuggestion suggestion = new ReassignmentSuggestion(
                order,
                top.agent(),
                top.confidence(),
                top.reasoning(),
                triggerReason
        );
        suggestionRepository.save(suggestion);

        order.setStatus(OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        return SuggestionResponse.from(suggestion);
    }

    private List<Agent> pickCandidates(Order order) {
        List<Agent> available = agentRepository.findByStatusOrderByActiveOrderCountAsc(
                AgentStatus.AVAILABLE);
        String currentAssigneeId = order.getAssignedAgent() == null
                ? null : order.getAssignedAgent().getId();
        return available.stream()
                .filter(a -> currentAssigneeId == null || !a.getId().equals(currentAssigneeId))
                .toList();
    }
}
