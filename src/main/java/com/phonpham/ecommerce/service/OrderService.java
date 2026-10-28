package com.phonpham.ecommerce.service;

import com.phonpham.ecommerce.dto.CheckoutRequest;
import com.phonpham.ecommerce.dto.OrderItemResponse;
import com.phonpham.ecommerce.dto.OrderResponse;
import com.phonpham.ecommerce.entity.*;
import com.phonpham.ecommerce.exception.BadRequestException;
import com.phonpham.ecommerce.exception.InsufficientStockException;
import com.phonpham.ecommerce.exception.ResourceNotFoundException;
import com.phonpham.ecommerce.repository.CartRepository;
import com.phonpham.ecommerce.repository.OrderRepository;
import com.phonpham.ecommerce.repository.ProductRepository;
import com.phonpham.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    /**
     * HIGH CONCURRENCY CHECKOUT FLOW WITH PESSIMISTIC LOCKING
     *
     * 1. Acquires row-level PESSIMISTIC_WRITE lock on each Product row in PostgreSQL:
     *    SELECT ... FOR UPDATE
     * 2. Validates inventory stock under strict lock isolation.
     * 3. Deducts inventory stock to guarantee ZERO OVERSELLING during simultaneous checkout bursts.
     * 4. Evicts Redis product caches to maintain data freshness.
     * 5. Atomically commits order and flushes user cart.
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = {"products", "productList"}, allEntries = true)
    public OrderResponse checkout(Long userId, CheckoutRequest checkoutRequest) {
        log.info("Starting checkout transaction for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("User does not have an active shopping cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Shopping cart is empty. Add products before checking out.");
        }

        String orderNumber = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .status(OrderStatus.PAID)
                .shippingAddress(checkoutRequest.getShippingAddress())
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Long productId = cartItem.getProduct().getId();
            int requestedQuantity = cartItem.getQuantity();

            // PESSIMISTIC WRITE LOCK - Blocks concurrent threads until this transaction commits/rolls back
            Product lockedProduct = productRepository.findByIdWithPessimisticLock(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            if (!Boolean.TRUE.equals(lockedProduct.getActive())) {
                throw new BadRequestException("Product is discontinued: " + lockedProduct.getName());
            }

            if (lockedProduct.getStock() < requestedQuantity) {
                log.warn("Checkout failed due to insufficient stock. Product: {}, Available: {}, Requested: {}",
                        lockedProduct.getName(), lockedProduct.getStock(), requestedQuantity);
                throw new InsufficientStockException("Insufficient stock for product '" + lockedProduct.getName() +
                        "'. Available: " + lockedProduct.getStock() + ", requested: " + requestedQuantity);
            }

            // Deduct stock safely under pessimistic lock
            int updatedStock = lockedProduct.getStock() - requestedQuantity;
            lockedProduct.setStock(updatedStock);
            productRepository.save(lockedProduct);

            BigDecimal unitPrice = lockedProduct.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            grandTotal = grandTotal.add(subtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(lockedProduct)
                    .quantity(requestedQuantity)
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(orderItem);
        }

        order.setTotalAmount(grandTotal);
        Order savedOrder = orderRepository.save(order);

        // Clear cart after successful checkout
        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Order successfully placed: {} for user: {} with total: {}",
                savedOrder.getOrderNumber(), user.getUsername(), grandTotal);

        return mapToOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(Long userId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with order number: " + orderNumber));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("You are not authorized to view this order");
        }

        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(this::mapToOrderResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::mapToOrderResponse);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        order.setStatus(newStatus);
        return mapToOrderResponse(orderRepository.save(order));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            itemResponses.add(OrderItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .productSku(item.getProduct().getSku())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(item.getSubtotal())
                    .build());
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .userEmail(order.getUser().getEmail())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
