package com.example.demo.repository;

import com.example.demo.entity.ReassignmentSuggestion;
import com.example.demo.entity.SuggestionStatus;
import com.example.demo.entity.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReassignmentSuggestionRepository extends JpaRepository<ReassignmentSuggestion, Long> {

    // All suggestions for a specific order (useful for displaying history in the UI)
    List<ReassignmentSuggestion> findByOrderId(String orderId);

    // All suggestions currently awaiting ops action
    List<ReassignmentSuggestion> findByStatus(SuggestionStatus status);

    // T-4 idempotency check: is there already a PENDING AGENT_OFFLINE suggestion for this order?
    // Uses the composite index: idx_suggestion_order_status_trigger
    Optional<ReassignmentSuggestion> findByOrderIdAndStatusAndTriggerReason(
            String orderId,
            SuggestionStatus status,
            TriggerReason triggerReason
    );

    // All suggestions recommended to a specific agent (audit / reporting)
    List<ReassignmentSuggestion> findByRecommendedAgentId(String agentId);
}
