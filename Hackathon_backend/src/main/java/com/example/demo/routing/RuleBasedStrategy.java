package com.example.demo.routing;

import com.example.demo.entity.Agent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Deterministic fallback strategy: recommend the candidate with the fewest
 * active orders. No external dependencies — safe to use as the fallback
 * when the AI strategy fails (T-3).
 */
@Component
public class RuleBasedStrategy implements RoutingStrategy {

    public static final String NAME = "rule-based";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Recommendation> recommend(RoutingContext ctx) {
        if (ctx.candidates() == null || ctx.candidates().isEmpty()) {
            return List.of();
        }

        List<Agent> ranked = ctx.candidates().stream()
                .sorted(Comparator.comparingInt(Agent::getActiveOrderCount)
                        .thenComparing(Agent::getId))
                .toList();

        int total = ranked.size();
        return ranked.stream()
                .map(agent -> new Recommendation(
                        agent,
                        0.6,
                        "Selected " + agent.getId() + " (" + agent.getName()
                                + "): " + agent.getActiveOrderCount()
                                + " active order(s), fewest among " + total
                                + " available agent(s)."))
                .toList();
    }
}
