package com.example.demo.controller;

import com.example.demo.api.dto.CreateOrderRequest;
import com.example.demo.api.dto.OrderResponse;
import com.example.demo.api.dto.SuggestionResponse;
import com.example.demo.entity.OrderStatus;
import com.example.demo.entity.TriggerReason;
import com.example.demo.service.OrderService;
import com.example.demo.service.RoutingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final RoutingService routingService;

    public OrderController(OrderService orderService, RoutingService routingService) {
        this.orderService = orderService;
        this.routingService = routingService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<OrderResponse> listOrders(@RequestParam(required = false) OrderStatus status) {
        return orderService.listOrders(status);
    }

    @PostMapping("/{id}/suggest")
    public SuggestionResponse suggest(@PathVariable String id) {
        return routingService.suggestForOrderId(id, TriggerReason.INITIAL);
    }
}
