package com.example.demo.api.dto;

import com.example.demo.entity.AgentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateAgentStatusRequest {

    @NotNull(message = "status is required")
    private AgentStatus status;
}
