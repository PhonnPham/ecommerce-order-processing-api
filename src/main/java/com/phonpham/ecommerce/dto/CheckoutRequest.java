package com.phonpham.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckoutRequest {
    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    private String paymentMethod;
}
