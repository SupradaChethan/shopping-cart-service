# Technical Documentation: Spring Boot Shopping Cart with Azure Cosmos DB

**Author:** Suprada Chethan
**Purpose:** Certification & Interview Reference
**Tech Stack:** Spring Boot 3.2.0, Azure Cosmos DB, Java 17, Maven

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Azure Cosmos DB Implementation](#azure-cosmos-db-implementation)
3. [Entity Modeling](#entity-modeling)
4. [Repository Layer](#repository-layer)
5. [Service Layer](#service-layer)
6. [REST API Layer](#rest-api-layer)
7. [Configuration & Security](#configuration--security)
8. [Key Learnings & Best Practices](#key-learnings--best-practices)

---

## Architecture Overview

### System Architecture
```
┌─────────────────┐
│   Web Browser   │
│   (HTML/JS UI)  │
└────────┬────────┘
         │ HTTP
         ▼
┌─────────────────────────────────────┐
│      Spring Boot Application        │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   Controller Layer           │  │
│  │  - ProductController         │  │
│  │  - CartController            │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│  ┌──────────▼───────────────────┐  │
│  │   Service Layer              │  │
│  │  - ProductService            │  │
│  │  - CartService               │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│  ┌──────────▼───────────────────┐  │
│  │   Repository Layer           │  │
│  │  - ProductRepository         │  │
│  │  - CartRepository            │  │
│  └──────────┬───────────────────┘  │
│             │                       │
│  ┌──────────▼───────────────────┐  │
│  │  Spring Data Cosmos          │  │
│  │  (Azure SDK Integration)     │  │
│  └──────────┬───────────────────┘  │
└─────────────┼───────────────────────┘
              │
              ▼
    ┌──────────────────┐
    │  Azure Cosmos DB │
    │   (NoSQL Store)  │
    └──────────────────┘
```

### Design Pattern
- **MVC Pattern**: Separation of concerns (Controller → Service → Repository)
- **Repository Pattern**: Abstraction over data access
- **Dependency Injection**: Constructor-based injection for testability
- **RESTful API**: Resource-based endpoints with standard HTTP methods

---

## Azure Cosmos DB Implementation

### What is Azure Cosmos DB?
- **Type**: Globally distributed, multi-model NoSQL database
- **API**: This project uses the **SQL API** (also called Core API)
- **Data Model**: Document-based (JSON documents)
- **Consistency**: Tunable consistency levels (we use Session consistency by default)

### Why Cosmos DB?
1. **Global Distribution**: Multi-region replication
2. **Horizontal Scaling**: Automatic partitioning
3. **Low Latency**: Single-digit millisecond read/write
4. **Schema-less**: Flexible JSON documents
5. **Spring Integration**: Native Spring Data support

### Key Dependencies

```xml
<!-- Spring Cloud Azure Cosmos DB Starter -->
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-data-cosmos</artifactId>
</dependency>
```

This single dependency includes:
- `azure-cosmos`: Azure Cosmos DB Java SDK
- `spring-data-commons`: Spring Data abstractions
- `spring-cloud-azure-core`: Azure integration
- Auto-configuration for Spring Boot

---

## Entity Modeling

### 1. Product Entity

```java
@Container(containerName = "products")
public class Product {
    @Id
    private String id;

    @PartitionKey
    private String category;

    private String name;
    private String description;
    private Double price;
    private Integer stockQuantity;
}
```

**Key Concepts:**

#### @Container Annotation
- Maps Java class to Cosmos DB container (like a table)
- Container name: `products`
- Cosmos DB automatically creates this container on first use

#### @Id Annotation
- Unique identifier for the document
- Cosmos DB requires every document to have an `id` field
- Must be unique within the partition

#### @PartitionKey Annotation
- **Critical for Cosmos DB**: Determines how data is distributed
- Partition key: `category` (e.g., "Electronics", "Books")
- All products with the same category are stored together
- Enables efficient querying within a category
- **Important**: Cannot be changed after document creation

**Partitioning Strategy:**
```
Products Container
├── Partition: Electronics
│   ├── Product: "Laptop" (id: prod_001)
│   ├── Product: "Phone" (id: prod_002)
│   └── Product: "Tablet" (id: prod_003)
│
├── Partition: Books
│   ├── Product: "Java Guide" (id: prod_004)
│   └── Product: "Spring Boot" (id: prod_005)
│
└── Partition: Clothing
    └── Product: "T-Shirt" (id: prod_006)
```

### 2. Cart Entity

```java
@Container(containerName = "carts")
public class Cart {
    @Id
    private String id;

    @PartitionKey
    private String userId;

    private List<CartItem> items = new ArrayList<>();
}
```

**Partition Strategy:**
- Partition key: `userId`
- Each user has their own cart in their partition
- Efficient for user-specific queries
- Scalable as users grow

### 3. CartItem (Embedded Document)

```java
public class CartItem {
    private String productId;
    private String productName;
    private Double price;
    private Integer quantity;
}
```

**Design Decision:**
- **Embedded Document**: Not a separate container
- Stored as JSON array inside Cart document
- **Denormalization**: Includes product details (name, price)
- **Trade-off**: Faster reads, but requires updates if product details change

---

## Repository Layer

### Spring Data Cosmos Repository

```java
@Repository
public interface ProductRepository extends CosmosRepository<Product, String> {
    List<Product> findByCategory(String category);
}
```

**How It Works:**

#### CosmosRepository Interface
- Extends `PagingAndSortingRepository<T, ID>`
- Provides CRUD operations out-of-the-box
- Spring Data generates implementation at runtime

#### Built-in Methods (No Implementation Needed):
- `save(Product)` - Insert or update
- `findById(String)` - Retrieve by ID
- `findAll()` - Retrieve all documents
- `deleteById(String)` - Delete document
- `count()` - Count documents

#### Custom Query Methods:
```java
List<Product> findByCategory(String category);
```
- **Method Name Convention**: Spring parses method name
- Translates to Cosmos DB SQL:
  ```sql
  SELECT * FROM c WHERE c.category = @category
  ```
- Automatically uses the partition key for efficient queries

#### Behind the Scenes:
1. Spring Data creates proxy implementation
2. Uses Azure Cosmos SDK under the hood
3. Handles connection pooling, retries, and error handling
4. Manages partition key routing automatically

---

## Service Layer

### ProductService Implementation

```java
@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        productRepository.findAll().forEach(products::add);
        return products;
    }
}
```

**Key Points:**

#### Iterable to List Conversion
```java
productRepository.findAll().forEach(products::add);
```
- `findAll()` returns `Iterable<Product>`, not `List<Product>`
- Cosmos DB uses reactive programming internally
- Must convert to List for REST API compatibility
- **Common Pitfall**: Casting `(List<Product>)` causes `ClassCastException`

#### Constructor Injection
```java
public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
}
```
- No `@Autowired` needed in modern Spring
- Better for testing (can pass mock repositories)
- Final field ensures immutability

### CartService Implementation

```java
@Service
public class CartService {
    private final CartRepository cartRepository;
    private final ProductService productService;

    public Cart addItemToCart(String userId, String productId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);
        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Check if item already exists
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            // Update quantity
            existingItem.get().setQuantity(
                existingItem.get().getQuantity() + quantity
            );
        } else {
            // Add new item
            CartItem newItem = new CartItem(
                product.getId(),
                product.getName(),
                product.getPrice(),
                quantity
            );
            cart.getItems().add(newItem);
        }

        return cartRepository.save(cart);
    }
}
```

**Business Logic:**
1. Get or create cart for user
2. Fetch product details from ProductService
3. Check if product already in cart
4. Update quantity or add new item
5. Save entire cart document

**Cosmos DB Considerations:**
- Entire cart document is updated (no partial updates by default)
- Optimistic concurrency control via ETag (handled by SDK)
- Document size limit: 2MB (reasonable for shopping carts)

---

## REST API Layer

### ProductController

```java
@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Management", description = "APIs for managing products")
public class ProductController {

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(product));
    }

    @GetMapping
    @Operation(summary = "Get all products")
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }
}
```

**RESTful Design:**

| Method | Endpoint | Purpose | Status Code |
|--------|----------|---------|-------------|
| POST | `/api/products` | Create product | 201 Created |
| GET | `/api/products` | List all products | 200 OK |
| GET | `/api/products/{id}` | Get single product | 200 OK / 404 Not Found |
| PUT | `/api/products/{id}` | Update product | 200 OK |
| DELETE | `/api/products/{id}` | Delete product | 204 No Content |

**Swagger Integration:**
- `@Tag`: Groups endpoints in Swagger UI
- `@Operation`: Describes endpoint purpose
- Auto-generates OpenAPI 3.0 documentation
- Accessible at: `/swagger-ui.html`

---

## Configuration & Security

### Application Configuration

```properties
# Cosmos DB Configuration
spring.cloud.azure.cosmos.endpoint=${COSMOS_ENDPOINT}
spring.cloud.azure.cosmos.key=${COSMOS_KEY}
spring.cloud.azure.cosmos.database=${COSMOS_DATABASE:shopping-cart-db}
```

**Environment Variables:**
- `COSMOS_ENDPOINT`: Cosmos DB account URI
- `COSMOS_KEY`: Primary or secondary access key
- `COSMOS_DATABASE`: Database name (default: shopping-cart-db)

**Security Best Practices:**
1. **Never hardcode credentials** in application.properties
2. Use environment variables or Azure Key Vault
3. Add `.env` to `.gitignore`
4. Use managed identities in production (Azure App Service)

### Auto-Configuration

Spring Boot auto-configures:
1. **CosmosClient**: Connection to Cosmos DB
2. **CosmosTemplate**: Template for database operations
3. **Repository Proxies**: Implementation of repository interfaces
4. **Container Creation**: Automatically creates containers on startup

**Connection Settings (Default):**
- Connection Mode: `DIRECT` (TCP)
- Consistency Level: `SESSION`
- Retry Policy: Exponential backoff (9 retries, max 30s)
- Request Timeout: 60 seconds

---

## Key Learnings & Best Practices

### 1. Cosmos DB Specific

#### Partition Key Selection
✅ **Good Choices:**
- High cardinality (many unique values)
- Evenly distributed data
- Used in most queries
- Example: `userId`, `category`, `tenantId`

❌ **Bad Choices:**
- Low cardinality (few unique values)
- Boolean fields
- Timestamp (creates hot partitions)
- Example: `isActive`, `createdDate`

#### Query Optimization
```java
// ✅ GOOD: Uses partition key
List<Product> findByCategory(String category);

// ❌ BAD: Cross-partition query
List<Product> findByName(String name);
```

**Cross-partition queries:**
- More expensive (higher RU/s consumption)
- Slower performance
- Fan-out to all partitions

#### Request Units (RU/s)
- **1 RU** = Read 1KB document by ID + partition key
- Write operations cost more (5-10 RUs)
- Cross-partition queries cost more
- Monitor and tune RU/s allocation

### 2. Spring Data Cosmos

#### Common Pitfalls

**Iterable vs List:**
```java
// ❌ WRONG: ClassCastException at runtime
return (List<Product>) productRepository.findAll();

// ✅ CORRECT: Convert Iterable to List
List<Product> products = new ArrayList<>();
productRepository.findAll().forEach(products::add);
return products;
```

**Container Auto-Creation:**
- Containers created on first repository access
- Requires database to exist beforehand
- Partition key defined in `@Container` or `@PartitionKey`

### 3. Data Modeling

#### Embed vs Reference

**Embed (Denormalize):**
```java
class Cart {
    List<CartItem> items; // ✅ Embedded
}
```
- Faster reads (single query)
- Document size limit: 2MB
- Data duplication
- Good for: One-to-few relationships

**Reference (Normalize):**
```java
class Order {
    String userId; // ❌ Reference, not embedded
}
```
- Requires multiple queries
- No data duplication
- Better for: One-to-many relationships

### 4. Production Considerations

#### Monitoring
- Request Units consumed
- Latency metrics
- Throttling (429 errors)
- Connection pool health

#### Scaling
- Increase RU/s provisioning
- Use autoscale mode
- Implement caching (Redis)
- Consider partition strategy

#### Error Handling
```java
try {
    return productRepository.save(product);
} catch (CosmosException e) {
    if (e.getStatusCode() == 409) {
        // Conflict: Document already exists
    } else if (e.getStatusCode() == 429) {
        // Too many requests: Throttling
    }
    throw e;
}
```

---

## Interview Questions & Answers

### Q1: Why did you choose Cosmos DB for this project?

**Answer:**
"I chose Azure Cosmos DB for its globally distributed nature and native JSON document support. For a shopping cart service, we need:
1. **Low latency** for read/write operations - Cosmos DB provides single-digit millisecond response times
2. **Flexible schema** - Product catalogs often change, and NoSQL allows schema evolution
3. **Horizontal scaling** - As the application grows, Cosmos DB automatically partitions data
4. **Spring Integration** - Spring Data Cosmos provides familiar repository abstractions

Additionally, Cosmos DB's tunable consistency and multi-region replication make it production-ready for global applications."

### Q2: Explain your partition key strategy

**Answer:**
"I used different partition keys based on access patterns:

**Products Container - `category` partition key:**
- Products are often queried by category (Electronics, Books, etc.)
- Categories provide good distribution (many unique values)
- Enables efficient filtered queries

**Carts Container - `userId` partition key:**
- Each user accesses only their cart
- Natural isolation between users
- Scales horizontally as user base grows

This strategy ensures most queries are single-partition, minimizing RU consumption and latency."

### Q3: How does Spring Data Cosmos work internally?

**Answer:**
"Spring Data Cosmos works through several layers:

1. **Repository Proxy**: Spring creates a proxy implementation of repository interfaces at runtime
2. **Query Translation**: Method names like `findByCategory` are parsed and converted to Cosmos DB SQL queries
3. **Azure SDK**: Under the hood, it uses the Azure Cosmos Java SDK v4 for actual communication
4. **Connection Management**: Maintains a connection pool with configurable retry policies
5. **Serialization**: Uses Jackson to convert POJOs to/from JSON documents

The `@Container` and `@PartitionKey` annotations provide metadata for container mapping and partition routing."

### Q4: What challenges did you face with Cosmos DB?

**Answer:**
"Key challenges and solutions:

1. **Iterable Return Type**: Repository `findAll()` returns `Iterable`, not `List`. I converted it properly to avoid ClassCastException.

2. **Partition Key Immutability**: Cannot change partition key after creation. Required careful upfront design.

3. **Secret Management**: Initially hardcoded credentials, but GitHub blocked the push. Implemented environment variable configuration.

4. **Auto-Configuration Conflicts**: Created custom CosmosConfig that conflicted with Spring Boot's auto-config. Removed it to rely on auto-configuration.

5. **Cross-Partition Queries**: Learned to design queries that use partition keys for efficiency."

### Q5: How would you optimize this for production?

**Answer:**
"Production optimizations:

**Performance:**
- Implement caching layer (Redis) for frequently accessed products
- Use composite indexes for common query patterns
- Enable autoscale RU/s for variable load
- Implement pagination for large result sets

**Security:**
- Use Azure Managed Identity instead of connection strings
- Implement Azure Key Vault for secrets
- Enable Cosmos DB firewall rules
- Add authentication/authorization (Spring Security + JWT)

**Reliability:**
- Configure multi-region writes for high availability
- Implement circuit breaker pattern (Resilience4j)
- Add comprehensive error handling and retry logic
- Monitor with Application Insights

**Cost:**
- Use serverless mode for dev/test environments
- Implement TTL for temporary data (e.g., abandoned carts)
- Optimize query patterns to reduce RU consumption"

---

## Code Snippets Reference

### Complete Entity Example
```java
@Container(containerName = "products", autoCreateContainer = true)
public class Product {
    @Id
    private String id;

    @PartitionKey
    private String category;

    private String name;
    private String description;
    private Double price;
    private Integer stockQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors, getters, setters
}
```

### Repository with Custom Queries
```java
@Repository
public interface ProductRepository extends CosmosRepository<Product, String> {
    // Derived query - single partition
    List<Product> findByCategory(String category);

    // Derived query - cross partition
    List<Product> findByPriceLessThan(Double price);

    // Custom query with @Query annotation
    @Query("SELECT * FROM c WHERE c.category = @category AND c.price < @maxPrice")
    List<Product> findByCategoryAndPriceLessThan(
        @Param("category") String category,
        @Param("maxPrice") Double maxPrice
    );
}
```

### Service with Transaction-like Behavior
```java
@Service
public class OrderService {
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public Order checkout(String userId) {
        Cart cart = cartRepository.findByUserId(userId)
            .orElseThrow(() -> new CartNotFoundException(userId));

        // Validate inventory
        for (CartItem item : cart.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new ProductNotFoundException());

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException();
            }
        }

        // Create order and clear cart
        Order order = new Order();
        order.setUserId(userId);
        order.setItems(cart.getItems());

        cart.getItems().clear();
        cartRepository.save(cart);

        return order;
    }
}
```

---

## Additional Resources

### Official Documentation
- [Azure Cosmos DB Documentation](https://docs.microsoft.com/azure/cosmos-db/)
- [Spring Data Cosmos](https://docs.spring.io/spring-cloud-azure/docs/current/reference/html/#spring-data-support)
- [Azure Cosmos DB Best Practices](https://docs.microsoft.com/azure/cosmos-db/best-practice-guide)

### Performance Tuning
- [Request Units in Cosmos DB](https://docs.microsoft.com/azure/cosmos-db/request-units)
- [Partitioning in Cosmos DB](https://docs.microsoft.com/azure/cosmos-db/partitioning-overview)
- [Indexing Policies](https://docs.microsoft.com/azure/cosmos-db/index-policy)

### Spring Boot
- [Spring Boot Auto-configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.auto-configuration)
- [Spring Data Repositories](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories)

---

## Summary

This shopping cart service demonstrates:

✅ **Spring Boot 3.x** with modern Java 17+ features
✅ **Azure Cosmos DB** integration using Spring Data
✅ **Repository Pattern** for clean architecture
✅ **RESTful API** design principles
✅ **Swagger/OpenAPI** documentation
✅ **Environment-based** configuration
✅ **Security best practices** (no hardcoded secrets)
✅ **Partition strategy** for scalable NoSQL design

**Key Takeaway:**
This project showcases practical NoSQL database design with Azure Cosmos DB, demonstrating understanding of partition keys, data modeling trade-offs, and Spring ecosystem integration - essential skills for cloud-native Java development.

---

**Document Version:** 1.0
**Last Updated:** December 2024
**GitHub:** https://github.com/SupradaChethan/shopping-cart-service
