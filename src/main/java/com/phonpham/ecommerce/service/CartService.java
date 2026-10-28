package com.phonpham.ecommerce.service;

import com.phonpham.ecommerce.dto.CartItemRequest;
import com.phonpham.ecommerce.dto.CartItemResponse;
import com.phonpham.ecommerce.dto.CartResponse;
import com.phonpham.ecommerce.entity.Cart;
import com.phonpham.ecommerce.entity.CartItem;
import com.phonpham.ecommerce.entity.Product;
import com.phonpham.ecommerce.entity.User;
import com.phonpham.ecommerce.exception.BadRequestException;
import com.phonpham.ecommerce.exception.InsufficientStockException;
import com.phonpham.ecommerce.exception.ResourceNotFoundException;
import com.phonpham.ecommerce.repository.CartItemRepository;
import com.phonpham.ecommerce.repository.CartRepository;
import com.phonpham.ecommerce.repository.ProductRepository;
import com.phonpham.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
            Cart cart = Cart.builder()
                    .user(user)
                    .items(new ArrayList<>())
                    .build();
            return cartRepository.save(cart);
        });
    }

    @Transactional(readOnly = true)
    public CartResponse getCartResponse(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse addToCart(Long userId, CartItemRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        if (!product.getActive()) {
            throw new BadRequestException("Product is not available for purchase");
        }

        if (product.getStock() < request.getQuantity()) {
            throw new InsufficientStockException("Insufficient stock for product " + product.getName() +
                    ". Available: " + product.getStock() + ", requested: " + request.getQuantity());
        }

        Cart cart = getOrCreateCart(userId);
        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQuantity = item.getQuantity() + request.getQuantity();
            if (product.getStock() < newQuantity) {
                throw new InsufficientStockException("Insufficient stock. Total requested in cart: " +
                        newQuantity + ", available: " + product.getStock());
            }
            item.setQuantity(newQuantity);
            item.setUnitPrice(product.getPrice());
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();
            cart.getItems().add(newItem);
            cartItemRepository.save(newItem);
        }

        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse updateCartItem(Long userId, Long cartItemId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("Cart item does not belong to user cart");
        }

        if (quantity <= 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            Product product = item.getProduct();
            if (product.getStock() < quantity) {
                throw new InsufficientStockException("Insufficient stock for product: " + product.getName());
            }
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }

        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse removeCartItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new BadRequestException("Cart item does not belong to user cart");
        }

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        return mapToCartResponse(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private CartResponse mapToCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem item : cart.getItems()) {
            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);
            totalItems += item.getQuantity();

            itemResponses.add(CartItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productSku(item.getProduct().getSku())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(subtotal)
                    .build());
        }

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemResponses)
                .totalItems(totalItems)
                .totalAmount(total)
                .build();
    }
}
