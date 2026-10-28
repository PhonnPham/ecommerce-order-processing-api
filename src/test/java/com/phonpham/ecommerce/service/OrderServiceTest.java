package com.phonpham.ecommerce.service;

import com.phonpham.ecommerce.dto.CheckoutRequest;
import com.phonpham.ecommerce.dto.OrderResponse;
import com.phonpham.ecommerce.entity.*;
import com.phonpham.ecommerce.exception.BadRequestException;
import com.phonpham.ecommerce.exception.InsufficientStockException;
import com.phonpham.ecommerce.repository.CartRepository;
import com.phonpham.ecommerce.repository.OrderRepository;
import com.phonpham.ecommerce.repository.ProductRepository;
import com.phonpham.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private Product testProduct;
    private Cart testCart;
    private CartItem testCartItem;
    private CheckoutRequest checkoutRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("phonpham")
                .email("vanphon741@gmail.com")
                .role(Role.ROLE_CUSTOMER)
                .build();

        testProduct = Product.builder()
                .id(10L)
                .name("MacBook Pro M3 Max")
                .sku("MBP-M3-MAX")
                .price(new BigDecimal("2999.00"))
                .stock(5)
                .active(true)
                .build();

        testCart = Cart.builder()
                .id(100L)
                .user(testUser)
                .items(new ArrayList<>())
                .build();

        testCartItem = CartItem.builder()
                .id(50L)
                .cart(testCart)
                .product(testProduct)
                .quantity(2)
                .unitPrice(testProduct.getPrice())
                .build();

        testCart.getItems().add(testCartItem);

        checkoutRequest = new CheckoutRequest();
        checkoutRequest.setShippingAddress("123 Tech Avenue, District 1, HCMC");
    }

    @Test
    @DisplayName("Checkout should succeed with Pessimistic Lock and deduct inventory stock")
    void checkout_Success_DeductsStock() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(testCart));
        // Verify pessimistic lock method is called
        when(productRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(testProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1000L);
            return o;
        });

        OrderResponse response = orderService.checkout(1L, checkoutRequest);

        assertNotNull(response);
        assertEquals(OrderStatus.PAID, response.getStatus());
        assertEquals(new BigDecimal("5998.00"), response.getTotalAmount());
        assertEquals(3, testProduct.getStock()); // 5 - 2 = 3
        assertTrue(testCart.getItems().isEmpty()); // Cart cleared

        verify(productRepository, times(1)).findByIdWithPessimisticLock(10L);
        verify(productRepository, times(1)).save(testProduct);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartRepository, times(1)).save(testCart);
    }

    @Test
    @DisplayName("Checkout should fail and throw InsufficientStockException when stock is less than requested")
    void checkout_ThrowsInsufficientStockException_WhenStockNotEnough() {
        testProduct.setStock(1); // Only 1 in stock, but cart requests 2

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(testCart));
        when(productRepository.findByIdWithPessimisticLock(10L)).thenReturn(Optional.of(testProduct));

        InsufficientStockException exception = assertThrows(InsufficientStockException.class, () ->
                orderService.checkout(1L, checkoutRequest)
        );

        assertTrue(exception.getMessage().contains("Insufficient stock"));
        assertEquals(1, testProduct.getStock()); // Stock unchanged

        verify(productRepository, times(1)).findByIdWithPessimisticLock(10L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Checkout should fail when shopping cart is empty")
    void checkout_ThrowsBadRequestException_WhenCartEmpty() {
        testCart.getItems().clear();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(testCart));

        assertThrows(BadRequestException.class, () ->
                orderService.checkout(1L, checkoutRequest)
        );

        verify(productRepository, never()).findByIdWithPessimisticLock(anyLong());
    }
}
