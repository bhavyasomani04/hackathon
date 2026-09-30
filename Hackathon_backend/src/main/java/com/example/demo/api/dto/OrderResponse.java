package com.example.demo.api.dto;

import com.example.demo.entity.Agent;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String description;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private String assignedAgentId;
    private String assignedAgentName;

    public static OrderResponse from(Order order) {
        Agent agent = order.getAssignedAgent();
        return new OrderResponse(
                order.getId(),
                order.getDescription(),
                order.getStatus(),
                order.getCreatedAt(),
                agent == null ? null : agent.getId(),
                agent == null ? null : agent.getName()
        );
    }
}
