package com.example.demo.routing;

import com.example.demo.entity.Agent;

/**
 * A single ranked recommendation produced by a {@link RoutingStrategy}.
 * The persistence layer maps this into a {@code ReassignmentSuggestion}.
 */
public record Recommendation(
        Agent agent,
        double confidence,
        String reasoning
) {}
