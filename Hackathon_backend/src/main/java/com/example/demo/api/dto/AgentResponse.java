package com.example.demo.api.dto;

import com.example.demo.entity.Agent;
import com.example.demo.entity.AgentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AgentResponse {

    private String id;
    private String name;
    private AgentStatus status;
    private int activeOrderCount;

    public static AgentResponse from(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getStatus(),
                agent.getActiveOrderCount()
        );
    }
}
