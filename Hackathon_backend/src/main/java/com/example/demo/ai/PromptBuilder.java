package com.example.demo.ai;

import com.example.demo.entity.Agent;
import com.example.demo.entity.Order;
import com.example.demo.entity.TriggerReason;
import com.example.demo.routing.RoutingContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the two distinct prompts the brief asks for.
 *
 * <ul>
 *   <li><b>INITIAL</b> — a normal dispatch prompt.</li>
 *   <li><b>REPLAN</b>  — a situation report: a specific agent failed, N orders
 *       are now stranded, this is recovery. Not the initial prompt with a note
 *       appended.</li>
 * </ul>
 *
 * <p>The "failed agent" for a re-plan is inferred from
 * {@code order.getAssignedAgent()} — at re-plan time the order is still assigned
 * to the offline agent (T-4 hasn't moved it yet).
 */
@Component
public class PromptBuilder {

    public String build(RoutingContext ctx) {
        return ctx.triggerReason() == TriggerReason.AGENT_OFFLINE
                ? buildReplanPrompt(ctx)
                : buildInitialPrompt(ctx);
    }

    private String buildInitialPrompt(RoutingContext ctx) {
        Order order = ctx.order();
        StringBuilder sb = new StringBuilder();
        sb.append("You are the dispatcher for ZipRun, a same-day delivery service.\n\n");
        sb.append("TASK\n");
        sb.append("Pick the single best available delivery agent for the order below.\n");
        sb.append("Favour agents with fewer active orders unless something in the order justifies otherwise.\n\n");
        sb.append("ORDER\n");
        sb.append("  id:          ").append(order.getId()).append('\n');
        sb.append("  description: ").append(safe(order.getDescription())).append('\n');
        appendRoster(sb, ctx.candidates(), "AVAILABLE AGENTS");
        appendOutputContract(sb);
        return sb.toString();
    }

    private String buildReplanPrompt(RoutingContext ctx) {
        Order order = ctx.order();
        Agent failed = order.getAssignedAgent();
        String failedId   = failed == null ? "(unknown)" : failed.getId();
        String failedName = failed == null ? "(unknown)" : failed.getName();

        StringBuilder sb = new StringBuilder();
        sb.append("You are the dispatcher for ZipRun. This is a RECOVERY reassignment, not a fresh dispatch.\n\n");
        sb.append("SITUATION\n");
        sb.append("  Agent ").append(failedId).append(" (").append(failedName)
                .append(") has just gone OFFLINE.\n");
        sb.append("  Their previous assignment for the order below is void and must be re-planned.\n");
        sb.append("  This is one of possibly several stranded orders; recover this one.\n\n");
        sb.append("ORDER TO REASSIGN\n");
        sb.append("  id:              ").append(order.getId()).append('\n');
        sb.append("  description:     ").append(safe(order.getDescription())).append('\n');
        sb.append("  original agent:  ").append(failedId).append(" (NOW OFFLINE — do not pick)\n");
        appendRoster(sb, ctx.candidates(), "REPLACEMENT AGENTS");
        sb.append("GUIDANCE\n");
        sb.append("  This is recovery, so prefer agents with real capacity headroom over marginal load differences.\n");
        sb.append("  You must pick from the replacement list only.\n\n");
        appendOutputContract(sb);
        return sb.toString();
    }

    private void appendRoster(StringBuilder sb, List<Agent> agents, String heading) {
        sb.append('\n').append(heading).append(" (id | name | active_orders)\n");
        if (agents == null || agents.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Agent a : agents) {
                sb.append("  ").append(a.getId())
                        .append(" | ").append(a.getName())
                        .append(" | ").append(a.getActiveOrderCount()).append('\n');
            }
        }
        sb.append('\n');
    }

    private void appendOutputContract(StringBuilder sb) {
        sb.append("OUTPUT\n");
        sb.append("Respond with ONLY a JSON object matching this exact shape — no prose, no code fences:\n");
        sb.append("{\"agentId\":\"<id copied verbatim from the list above>\",");
        sb.append("\"confidence\":<number between 0.0 and 1.0>,");
        sb.append("\"reasoning\":\"<one short sentence explaining the choice>\"}\n");
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\n", " ").trim();
    }
}
