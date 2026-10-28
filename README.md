# E-Commerce RESTful API & Order Processing System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

An enterprise-grade, high-performance E-Commerce RESTful API and Order Processing System engineered with **Spring Boot 3**, **Java 21**, **PostgreSQL**, and **Redis**. Designed specifically to handle high-concurrency checkout traffic with **zero overselling** guarantees and ultra-low read latency.

**Author:** Pham Van Phon  
**Email:** [vanphon741@gmail.com](mailto:vanphon741@gmail.com)  
**GitHub:** [https://github.com/PhonnPham](https://github.com/PhonnPham)

---

## Architecture Overview

```
                        +----------------------------+
                        |      Client / Frontend     |
                        +--------------+-------------+
                                       |
                         HTTP / REST   | (Bearer JWT)
                                       v
                        +----------------------------+
                        |  Spring Security 6 Filter  |
                        |   (Stateless JWT Auth)     |
                        +--------------+-------------+
                                       |
                                       v
                        +----------------------------+
                        |    REST Controllers        |
                        | (Auth, Product, Cart, Ord) |
                        +--------------+-------------+
                                       |
                   +-------------------+-------------------+
                   |                                       |
                   v (Catalog Queries)                     v (Checkout & Stock)
        +----------------------+               +-----------------------+
        |   Redis Cache Layer  |               |    Order Service      |
        |  (@Cacheable / TTL)  |               |  (Transactional Flow) |
        +----------------------+               +-----------+-----------+
                   | (Cache Miss)                          |
                   |                                       v (Pessimistic Lock)
                   |                           +-----------------------+
                   +-------------------------->|  PostgreSQL Database  |
                                               |  (SELECT FOR UPDATE)  |
                                               +-----------------------+
```

---

## Key Engineering Highlights

### 1. High-Concurrency Concurrency Control: Zero Overselling
During flash sales and peak shopping events, hundreds of concurrent requests may target the same inventory items simultaneously.
- **Pessimistic Write Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)**:
  - Implemented directly on the repository layer:
    ```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdWithPessimisticLock(@Param("id") Long id);
    ```
  - Triggers native PostgreSQL row-level locks (`SELECT ... FOR UPDATE`), ensuring serial execution on the critical inventory counter.
  - Guarantees **zero overselling** and eliminates race conditions under heavy concurrent checkout spikes.
  - Automatically rolls back the entire transaction if any product in the shopping cart has insufficient stock.

### 2. Multi-Tier Redis Caching Strategy
- Product detail lookups and paginated product catalog requests are cached in Redis via Spring Data Redis.
- **Fine-Grained TTL Configuration**:
  - `products`: 30 minutes TTL for high-demand item pages.
  - `productList`: 10 minutes TTL for search & category listings.
- **Cache Eviction (`@CacheEvict`)**:
  - Automatically invalidates stale caches whenever an administrator updates product attributes or when an order successfully deducts stock.

### 3. Stateless Security & JWT Authentication
- Built on Spring Security 6 with strict stateless session management (`SessionCreationPolicy.STATELESS`).
- **Dual-Token Pattern**:
  - **Access Token**: Short-lived (15 minutes) signed with HMAC-SHA256 (`jjwt 0.12.6`).
  - **Refresh Token**: Long-lived (7 days) stored securely in the database for rolling token renewal.
- Role-Based Access Control (RBAC):
  - `PUBLIC`: Browse catalog, view product details, user registration/login.
  - `ROLE_CUSTOMER`: Shopping cart operations, order checkout, order history.
  - `ROLE_ADMIN`: Product creation/updating/deletion, category configuration, system-wide order management.

---

## Tech Stack

| Component | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.3.4 |
| **Persistence** | Spring Data JPA / Hibernate | 6.x |
| **Database** | PostgreSQL | 16 |
| **In-Memory Cache** | Redis / Lettuce | 7 |
| **Security** | Spring Security, JJWT | 6.x, 0.12.6 |
| **API Documentation** | SpringDoc OpenAPI 3 / Swagger UI | 2.6.0 |
| **Testing** | JUnit 5, Mockito | 5.x |
| **Containerization** | Docker & Docker Compose | Multi-stage |

---

## Quickstart with Docker Compose

### Prerequisites
- Docker Engine 24.0+
- Docker Compose v2.20+

### Run Stack
```bash
# Clone the repository
git clone https://github.com/PhonnPham/ecommerce-order-processing-api.git
cd ecommerce-order-processing-api

# Start PostgreSQL, Redis, and Application container
docker-compose up -d --build
```

The application will be live at: `http://localhost:8080`  
Swagger UI will be available at: `http://localhost:8080/swagger-ui.html`

---

## Local Development & Testing

### Running Tests
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home # or your Java 21 path
mvn clean test
```

### Compile & Package
```bash
mvn clean package -DskipTests
```

---

## REST API Specification

### Authentication (`/api/v1/auth`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Register new customer account | No |
| `POST` | `/api/v1/auth/login` | Login and receive Access + Refresh tokens | No |
| `POST` | `/api/v1/auth/refresh-token` | Exchange refresh token for new access token | No |

### Product Catalog (`/api/v1/products`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/products` | Get paginated product catalog (Redis cached) | No |
| `GET` | `/api/v1/products/{id}` | Get product details by ID (Redis cached) | No |
| `GET` | `/api/v1/products/category/{id}` | Filter products by category | No |
| `POST` | `/api/v1/products` | Create product (Evicts cache) | `ROLE_ADMIN` |
| `PUT` | `/api/v1/products/{id}` | Update product (Evicts cache) | `ROLE_ADMIN` |
| `DELETE` | `/api/v1/products/{id}` | Deactivate product (Evicts cache) | `ROLE_ADMIN` |

### Shopping Cart (`/api/v1/cart`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/cart` | View current user's shopping cart | `ROLE_CUSTOMER` |
| `POST` | `/api/v1/cart/items` | Add product item to cart | `ROLE_CUSTOMER` |
| `PUT` | `/api/v1/cart/items/{id}` | Update quantity of cart item | `ROLE_CUSTOMER` |
| `DELETE` | `/api/v1/cart/items/{id}` | Remove specific cart item | `ROLE_CUSTOMER` |
| `DELETE` | `/api/v1/cart` | Clear entire shopping cart | `ROLE_CUSTOMER` |

### Order Workflows (`/api/v1/orders`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/orders/checkout` | **Checkout with Pessimistic Locking** | `ROLE_CUSTOMER` |
| `GET` | `/api/v1/orders/my-orders` | View user's order history | `ROLE_CUSTOMER` |
| `GET` | `/api/v1/orders/{orderNumber}` | View single order details | `ROLE_CUSTOMER` |
| `GET` | `/api/v1/orders/admin/all` | List all orders across platform | `ROLE_ADMIN` |
| `PATCH` | `/api/v1/orders/admin/{id}/status` | Update order fulfillment status | `ROLE_ADMIN` |

---

## License
Distributed under the Apache 2.0 License. See `LICENSE` for more information.
