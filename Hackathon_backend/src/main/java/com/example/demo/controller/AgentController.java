package com.example.demo.controller;

import com.example.demo.api.dto.AgentResponse;
import com.example.demo.api.dto.UpdateAgentStatusRequest;
import com.example.demo.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public List<AgentResponse> list() {
        return agentService.list();
    }

    @PatchMapping("/{id}/status")
    public AgentResponse updateStatus(@PathVariable String id,
                                      @Valid @RequestBody UpdateAgentStatusRequest request) {
        return agentService.updateStatus(id, request.getStatus());
    }
}
