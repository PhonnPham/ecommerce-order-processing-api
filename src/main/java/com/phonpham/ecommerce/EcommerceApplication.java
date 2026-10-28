package com.phonpham.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * E-Commerce RESTful API & Order Processing System
 * Author: Pham Van Phon <vanphon741@gmail.com>
 */
@SpringBootApplication
@EnableCaching
public class EcommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
