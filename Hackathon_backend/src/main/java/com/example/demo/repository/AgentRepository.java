package com.example.demo.repository;

import com.example.demo.entity.Agent;
import com.example.demo.entity.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {

    // Find all agents currently in a given status (e.g. AVAILABLE, OFFLINE)
    List<Agent> findByStatus(AgentStatus status);

    // Find agents whose active order count is below a threshold — useful for load-balancing
    List<Agent> findByActiveOrderCountLessThan(int maxLoad);

    // Find available agents with fewest active orders for simple rule-based routing
    List<Agent> findByStatusOrderByActiveOrderCountAsc(AgentStatus status);
}
