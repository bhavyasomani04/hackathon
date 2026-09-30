package com.example.demo.entity;

public enum AgentStatus {
    AVAILABLE,
    BUSY,
    /** Agent has gone offline — triggers the T-4 agentic re-planning loop. */
    OFFLINE
}
