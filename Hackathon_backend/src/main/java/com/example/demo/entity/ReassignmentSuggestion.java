package com.example.demo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Represents a single AI-generated reassignment recommendation for one order.
 *
 * <p>Lifecycle:
 * <pre>
 *   [T-4 loop / PATCH /agents/{id}/status]
 *         │
 *         ▼
 *      PENDING  ──── ops accepts ──▶  ACCEPTED
 *         │                            (order → REASSIGNED, agent loads updated)
 *         └──── ops rejects ──────▶  REJECTED
 * </pre>
 *
 * <p>The {@link #triggerReason} field is the seam connecting this entity to:
 * <ul>
 *   <li>T-4 agentic loop — loop sets {@code AGENT_OFFLINE} when it fires.</li>
 *   <li>T-5 UI — renders an "AGENTIC" badge when {@code triggerReason == AGENT_OFFLINE}.</li>
 * </ul>
 *
 * <p>Idempotency invariant (enforced in T-4): at most one {@code PENDING}
 * suggestion with {@code triggerReason=AGENT_OFFLINE} may exist per order at a time.
 */
@Entity
@Table(
    name = "reassignment_suggestions",
    indexes = {
        // Fast lookup for T-4 idempotency check: find PENDING AGENT_OFFLINE
        // suggestion for a given order without a full-table scan.
        @Index(name = "idx_suggestion_order_status_trigger",
               columnList = "order_id, status, trigger_reason")
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString
public class ReassignmentSuggestion {

    // ── Primary key ───────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Core relationships ────────────────────────────────────────────────────

    /**
     * The order that needs to be reassigned.
     * LAZY because we often only need the order ID for the idempotency check.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * The agent the AI recommends for this order.
     * Nullable at INSERT time (persisted immediately with what the AI returned);
     * a null here means the AI returned an unknown/invalid ID and the fallback
     * rule-based strategy also failed — surface this as an error state in the UI.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_agent_id")
    private Agent recommendedAgent;

    // ── AI output ─────────────────────────────────────────────────────────────

    /**
     * Confidence score returned by the LLM, validated to [0.0, 1.0].
     */
    @NotNull
    @DecimalMin(value = "0.0", message = "Confidence must be ≥ 0.0")
    @DecimalMax(value = "1.0", message = "Confidence must be ≤ 1.0")
    @Column(name = "confidence", nullable = false)
    private Double confidence;

    /**
     * Plain-English rationale produced by the LLM — shown verbatim in the ops UI.
     */
    @NotNull
    @Column(name = "reasoning", nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    // ── State machine ─────────────────────────────────────────────────────────

    /**
     * Current lifecycle state. Transitions: PENDING → ACCEPTED | REJECTED (one-way).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    /**
     * What caused this suggestion to be created.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 20)
    private TriggerReason triggerReason;

    // ── Audit timestamps ──────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Set when ops clicks Accept or Reject. Null while still PENDING. */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // ── Lifecycle callbacks ───────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ── Convenience constructor ───────────────────────────────────────────────

    /**
     * Used by both the rule-based and AI routing strategies.
     * No-arg constructor is handled by @NoArgsConstructor.
     */
    public ReassignmentSuggestion(Order order,
                                   Agent recommendedAgent,
                                   Double confidence,
                                   String reasoning,
                                   TriggerReason triggerReason) {
        this.order = order;
        this.recommendedAgent = recommendedAgent;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.triggerReason = triggerReason;
        this.status = SuggestionStatus.PENDING;
    }

    // ── Domain helper ─────────────────────────────────────────────────────────

    /**
     * Resolve this suggestion (called from PATCH /suggestions/{id}).
     * Sets status + resolvedAt in one operation so callers can't forget the timestamp.
     */
    public void resolve(SuggestionStatus resolution) {
        if (resolution == SuggestionStatus.PENDING) {
            throw new IllegalArgumentException("Cannot resolve to PENDING");
        }
        this.status = resolution;
        this.resolvedAt = LocalDateTime.now();
    }
}
