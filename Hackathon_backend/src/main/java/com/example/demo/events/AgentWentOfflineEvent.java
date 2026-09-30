package com.example.demo.events;

import java.time.LocalDateTime;

/**
 * Published when an agent's status transitions to OFFLINE.
 * The T-4 agentic re-planning loop will consume this via @Async @EventListener.
 */
public record AgentWentOfflineEvent(String agentId, LocalDateTime at) {
    public AgentWentOfflineEvent(String agentId) {
        this(agentId, LocalDateTime.now());
    }
}
