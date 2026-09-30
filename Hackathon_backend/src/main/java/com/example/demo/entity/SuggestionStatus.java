package com.example.demo.entity;

public enum SuggestionStatus {
    /** Awaiting ops team action in the dashboard. */
    PENDING,
    /** Ops approved — order will be moved to recommendedAgent. */
    ACCEPTED,
    /** Ops rejected — suggestion is discarded; order stays unchanged. */
    REJECTED
}
