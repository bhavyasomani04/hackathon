package com.example.demo.routing;

import com.example.demo.entity.Agent;
import com.example.demo.entity.Order;
import com.example.demo.entity.TriggerReason;

import java.util.List;

/**
 * Value object passed to every {@link RoutingStrategy}.
 *
 * <p>Wrapping the arguments (order, candidates, triggerReason) in a context
 * object means the interface stays stable when a future strategy needs more
 * inputs (e.g. T-3 AI strategy: agent-load snapshot; sprint-2: pickup zone).
 * Add a field here — no existing strategy breaks.
 */
public record RoutingContext(
        Order order,
        List<Agent> candidates,
        TriggerReason triggerReason
) {}
