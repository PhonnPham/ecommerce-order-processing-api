package com.phonpham.ecommerce.controller;

import com.phonpham.ecommerce.dto.CartItemRequest;
import com.phonpham.ecommerce.dto.CartResponse;
import com.phonpham.ecommerce.security.UserPrincipal;
import com.phonpham.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Shopping Cart", description = "Shopping cart management APIs for authenticated customers")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get current user's active shopping cart")
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(cartService.getCartResponse(userPrincipal.getId()));
    }

    @PostMapping("/items")
    @Operation(summary = "Add a product item to shopping cart")
    public ResponseEntity<CartResponse> addToCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addToCart(userPrincipal.getId(), request));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update quantity of an item in shopping cart")
    public ResponseEntity<CartResponse> updateCartItem(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long itemId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(cartService.updateCartItem(userPrincipal.getId(), itemId, quantity));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove an item from shopping cart")
    public ResponseEntity<CartResponse> removeFromCart(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(cartService.removeCartItem(userPrincipal.getId(), itemId));
    }

    @DeleteMapping
    @Operation(summary = "Clear all items from shopping cart")
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        cartService.clearCart(userPrincipal.getId());
        return ResponseEntity.noContent().build();
    }
}
