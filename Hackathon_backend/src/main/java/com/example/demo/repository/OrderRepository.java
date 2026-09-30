package com.example.demo.repository;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    // All orders currently assigned to a specific agent
    List<Order> findByAssignedAgentId(String agentId);

    // All orders in a given status (e.g. REASSIGNMENT_PENDING)
    List<Order> findByStatus(OrderStatus status);

    // Orders assigned to a specific agent filtered by status
    List<Order> findByAssignedAgentIdAndStatus(String agentId, OrderStatus status);
}
