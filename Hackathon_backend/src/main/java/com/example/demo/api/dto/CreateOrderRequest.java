package com.example.demo.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "Order id is required")
    private String id;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "agentId is required (order is pre-assigned)")
    private String agentId;
}
