package com.example.demo.routing;

import java.util.List;

/**
 * Contract for picking the best available agent(s) for an order.
 *
 * <p>Implementations register themselves as Spring beans under a stable
 * name (e.g. {@code @Component("rule-based")}) so
 * {@link RoutingStrategyRegistry} can dispatch by that name from config.
 *
 * <p>Adding a new strategy (sprint-2 {@code ZoneAffinityStrategy}, T-3 AI)
 * means implementing this interface and annotating the bean — no changes
 * to existing code.
 */
public interface RoutingStrategy {

    /** Stable identifier used by {@code routing.strategy} config. */
    String name();

    /** Ranked recommendations, best first. Empty list is a valid answer. */
    List<Recommendation> recommend(RoutingContext context);
}
