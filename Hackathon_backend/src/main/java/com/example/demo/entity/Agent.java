package com.example.demo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "orders")   // exclude to avoid lazy-load in logs
public class Agent {

    @Id
    @Column(name = "id", nullable = false, length = 20)
    private String id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Min(0)
    @Column(name = "active_order_count", nullable = false)
    private int activeOrderCount = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AgentStatus status;

    @OneToMany(mappedBy = "assignedAgent", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Order> orders = new ArrayList<>();

    // Convenience constructor (no-arg is handled by @NoArgsConstructor)
    public Agent(String id, String name, int activeOrderCount, AgentStatus status) {
        this.id = id;
        this.name = name;
        this.activeOrderCount = activeOrderCount;
        this.status = status;
    }
}
