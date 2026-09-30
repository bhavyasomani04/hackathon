package com.example.demo.service;

import com.example.demo.api.dto.AgentResponse;
import com.example.demo.entity.Agent;
import com.example.demo.entity.AgentStatus;
import com.example.demo.events.AgentWentOfflineEvent;
import com.example.demo.exception.AgentNotFoundException;
import com.example.demo.repository.AgentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AgentService {

    private final AgentRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentService(AgentRepository agentRepository, ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list() {
        return agentRepository.findAll().stream()
                .map(AgentResponse::from)
                .toList();
    }

    @Transactional
    public AgentResponse updateStatus(String agentId, AgentStatus newStatus) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new AgentNotFoundException(agentId));

        AgentStatus oldStatus = agent.getStatus();
        if (oldStatus == newStatus) {
            return AgentResponse.from(agent);
        }

        agent.setStatus(newStatus);
        agentRepository.save(agent);

        // Seam for T-4: publish the event, don't inline the loop.
        // Endpoint returns immediately; @Async listener will handle re-planning.
        if (newStatus == AgentStatus.OFFLINE) {
            eventPublisher.publishEvent(new AgentWentOfflineEvent(agent.getId()));
        }

        return AgentResponse.from(agent);
    }
}
