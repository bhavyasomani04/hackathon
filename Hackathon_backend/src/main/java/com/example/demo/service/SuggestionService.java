package com.example.demo.service;

import com.example.demo.api.dto.SuggestionResponse;
import com.example.demo.entity.Agent;
import com.example.demo.entity.AgentStatus;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.ReassignmentSuggestion;
import com.example.demo.entity.SuggestionStatus;
import com.example.demo.exception.IllegalAgentStateException;
import com.example.demo.exception.IllegalSuggestionStateException;
import com.example.demo.exception.SuggestionNotFoundException;
import com.example.demo.repository.AgentRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ReassignmentSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionService {

    private final ReassignmentSuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    public SuggestionService(ReassignmentSuggestionRepository suggestionRepository,
                             OrderRepository orderRepository,
                             AgentRepository agentRepository) {
        this.suggestionRepository = suggestionRepository;
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional(readOnly = true)
    public java.util.List<SuggestionResponse> list(SuggestionStatus statusOrNull) {
        java.util.List<ReassignmentSuggestion> rows = (statusOrNull == null)
                ? suggestionRepository.findAll()
                : suggestionRepository.findByStatus(statusOrNull);
        return rows.stream().map(SuggestionResponse::from).toList();
    }

    @Transactional
    public SuggestionResponse resolve(Long id, SuggestionStatus target) {
        if (target == null || target == SuggestionStatus.PENDING) {
            throw new IllegalSuggestionStateException(
                    "Target status must be ACCEPTED or REJECTED");
        }

        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new SuggestionNotFoundException(id));

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new IllegalSuggestionStateException(
                    "Suggestion " + id + " is already " + suggestion.getStatus());
        }

        if (target == SuggestionStatus.ACCEPTED) {
            applyAcceptance(suggestion);
        }
        // REJECTED: leave order + agents untouched per product decision.

        suggestion.resolve(target);
        return SuggestionResponse.from(suggestion);
    }

    private void applyAcceptance(ReassignmentSuggestion suggestion) {
        Agent newAgent = suggestion.getRecommendedAgent();
        if (newAgent == null) {
            throw new IllegalSuggestionStateException(
                    "Cannot accept suggestion " + suggestion.getId()
                            + ": no recommended agent (AI returned invalid id and fallback failed)");
        }
        if (newAgent.getStatus() == AgentStatus.OFFLINE) {
            throw new IllegalAgentStateException(
                    "Cannot accept: recommended agent '" + newAgent.getId() + "' is OFFLINE");
        }

        Order order = suggestion.getOrder();
        Agent oldAgent = order.getAssignedAgent();

        if (oldAgent != null && !oldAgent.getId().equals(newAgent.getId())) {
            int oldLoad = oldAgent.getActiveOrderCount();
            oldAgent.setActiveOrderCount(Math.max(0, oldLoad - 1));
            agentRepository.save(oldAgent);
        }

        if (oldAgent == null || !oldAgent.getId().equals(newAgent.getId())) {
            newAgent.setActiveOrderCount(newAgent.getActiveOrderCount() + 1);
            agentRepository.save(newAgent);
        }

        order.setAssignedAgent(newAgent);
        order.setStatus(OrderStatus.REASSIGNED);
        orderRepository.save(order);
    }
}
