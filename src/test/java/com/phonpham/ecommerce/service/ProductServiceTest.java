package com.phonpham.ecommerce.service;

import com.phonpham.ecommerce.dto.ProductRequest;
import com.phonpham.ecommerce.dto.ProductResponse;
import com.phonpham.ecommerce.entity.Product;
import com.phonpham.ecommerce.exception.ResourceNotFoundException;
import com.phonpham.ecommerce.repository.CategoryRepository;
import com.phonpham.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Get product by ID returns valid ProductResponse")
    void getProductById_Success() {
        Product product = Product.builder()
                .id(1L)
                .name("Keyboard Mechanical")
                .sku("KEY-001")
                .price(new BigDecimal("99.99"))
                .stock(50)
                .active(true)
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(1L);

        assertNotNull(response);
        assertEquals("Keyboard Mechanical", response.getName());
        assertEquals("KEY-001", response.getSku());
    }

    @Test
    @DisplayName("Get product by ID throws ResourceNotFoundException when product does not exist")
    void getProductById_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(999L));
    }

    @Test
    @DisplayName("Create product saves and returns new ProductResponse")
    void createProduct_Success() {
        ProductRequest request = new ProductRequest();
        request.setName("Gaming Mouse");
        request.setSku("MOU-001");
        request.setPrice(new BigDecimal("49.99"));
        request.setStock(100);

        when(productRepository.existsBySku("MOU-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("Gaming Mouse", response.getName());
        assertEquals(2L, response.getId());
        verify(productRepository, times(1)).save(any(Product.class));
    }
}
