package com.example.demo.api.dto;

import com.example.demo.entity.Agent;
import com.example.demo.entity.ReassignmentSuggestion;
import com.example.demo.entity.SuggestionStatus;
import com.example.demo.entity.TriggerReason;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SuggestionResponse {

    private Long id;
    private String orderId;
    private String recommendedAgentId;
    private String recommendedAgentName;
    private Double confidence;
    private String reasoning;
    private SuggestionStatus status;
    private TriggerReason triggerReason;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public static SuggestionResponse from(ReassignmentSuggestion s) {
        Agent agent = s.getRecommendedAgent();
        return new SuggestionResponse(
                s.getId(),
                s.getOrder() == null ? null : s.getOrder().getId(),
                agent == null ? null : agent.getId(),
                agent == null ? null : agent.getName(),
                s.getConfidence(),
                s.getReasoning(),
                s.getStatus(),
                s.getTriggerReason(),
                s.getCreatedAt(),
                s.getResolvedAt()
        );
    }
}
