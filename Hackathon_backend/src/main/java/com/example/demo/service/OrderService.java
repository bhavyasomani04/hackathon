package com.example.demo.service;

import com.example.demo.api.dto.CreateOrderRequest;
import com.example.demo.api.dto.OrderResponse;
import com.example.demo.entity.Agent;
import com.example.demo.entity.AgentStatus;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import com.example.demo.exception.AgentNotFoundException;
import com.example.demo.exception.DuplicateOrderException;
import com.example.demo.exception.IllegalAgentStateException;
import com.example.demo.repository.AgentRepository;
import com.example.demo.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    public OrderService(OrderRepository orderRepository, AgentRepository agentRepository) {
        this.orderRepository = orderRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        if (orderRepository.existsById(req.getId())) {
            throw new DuplicateOrderException(req.getId());
        }

        Agent agent = agentRepository.findById(req.getAgentId())
                .orElseThrow(() -> new AgentNotFoundException(req.getAgentId()));

        if (agent.getStatus() == AgentStatus.OFFLINE) {
            throw new IllegalAgentStateException(
                    "Cannot assign order to OFFLINE agent '" + agent.getId() + "'");
        }

        Order order = new Order(req.getId(), req.getDescription(), OrderStatus.ASSIGNED, agent);
        Order saved = orderRepository.save(order);

        agent.setActiveOrderCount(agent.getActiveOrderCount() + 1);
        agentRepository.save(agent);

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(OrderStatus status) {
        List<Order> orders = (status == null)
                ? orderRepository.findAll()
                : orderRepository.findByStatus(status);
        return orders.stream().map(OrderResponse::from).toList();
    }
}
