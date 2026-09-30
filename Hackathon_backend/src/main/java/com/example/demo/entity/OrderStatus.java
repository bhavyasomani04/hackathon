package com.example.demo.entity;

public enum OrderStatus {
    ASSIGNED,
    /** A ReassignmentSuggestion exists and is awaiting ops approval. */
    REASSIGNMENT_PENDING,
    /** Ops accepted a suggestion; order now belongs to the new agent. */
    REASSIGNED,
    DELIVERED
}
