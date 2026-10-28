package com.phonpham.ecommerce.controller;

import com.phonpham.ecommerce.dto.CheckoutRequest;
import com.phonpham.ecommerce.dto.OrderResponse;
import com.phonpham.ecommerce.entity.OrderStatus;
import com.phonpham.ecommerce.security.UserPrincipal;
import com.phonpham.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order Workflows", description = "Order checkout with PostgreSQL pessimistic locking and order management")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    @Operation(summary = "Checkout shopping cart with PostgreSQL Pessimistic Write Lock (Zero Overselling)")
    public ResponseEntity<OrderResponse> checkout(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.checkout(userPrincipal.getId(), request));
    }

    @GetMapping("/my-orders")
    @Operation(summary = "Get current authenticated customer order history")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getUserOrders(userPrincipal.getId(), pageable));
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "Get order details by order number")
    public ResponseEntity<OrderResponse> getOrderByNumber(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByNumber(userPrincipal.getId(), orderNumber));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all customer orders (ADMIN only)")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    @PatchMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order lifecycle status (ADMIN only)")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, status));
    }
}
