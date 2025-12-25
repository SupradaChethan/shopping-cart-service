# Shopping Cart Service

A simple Spring Boot shopping cart application with Azure Cosmos DB and Swagger API documentation.

## Features

- Product management (CRUD operations)
- Shopping cart functionality
- Cosmos DB integration
- Swagger/OpenAPI documentation
- Simple web UI for testing

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Azure Cosmos DB account

## Configuration

**IMPORTANT:** Never commit your Cosmos DB credentials to git. Use environment variables instead.

1. Copy the `.env.example` file to `.env`:
```bash
cp .env.example .env
```

2. Edit `.env` and add your actual Cosmos DB credentials:
```bash
COSMOS_ENDPOINT=https://your-cosmos-account.documents.azure.com:443/
COSMOS_KEY=your-actual-cosmos-key-here
COSMOS_DATABASE=shopping-cart-db
```

3. The `.env` file is already in `.gitignore` to prevent accidental commits.

Alternatively, set environment variables directly:
```bash
export COSMOS_ENDPOINT=https://your-cosmos-account.documents.azure.com:443/
export COSMOS_KEY=your-cosmos-key
export COSMOS_DATABASE=shopping-cart-db
```

## Build and Run

1. Build the project:
```bash
mvn clean install
```

2. Run the application:
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Access Points

- **Web UI**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/api-docs

## API Endpoints

### Products

- `POST /api/products` - Create a product
- `GET /api/products` - Get all products
- `GET /api/products/{id}` - Get product by ID
- `GET /api/products/category/{category}` - Get products by category
- `PUT /api/products/{id}` - Update a product
- `DELETE /api/products/{id}` - Delete a product

### Shopping Cart

- `GET /api/cart/{userId}` - Get user's cart
- `POST /api/cart/{userId}/items?productId={productId}&quantity={quantity}` - Add item to cart
- `PUT /api/cart/{userId}/items/{productId}?quantity={quantity}` - Update item quantity
- `DELETE /api/cart/{userId}/items/{productId}` - Remove item from cart
- `DELETE /api/cart/{userId}` - Clear cart

## Project Structure

```
src/main/java/com/shopping/cart/
├── ShoppingCartApplication.java    # Main application class
├── config/
│   └── OpenApiConfig.java         # Swagger configuration
├── controller/
│   ├── CartController.java        # Cart REST endpoints
│   └── ProductController.java     # Product REST endpoints
├── model/
│   ├── Cart.java                  # Cart entity
│   ├── CartItem.java              # Cart item model
│   └── Product.java               # Product entity
├── repository/
│   ├── CartRepository.java        # Cart Cosmos DB repository
│   └── ProductRepository.java     # Product Cosmos DB repository
└── service/
    ├── CartService.java           # Cart business logic
    └── ProductService.java        # Product business logic

src/main/resources/
├── application.properties         # Application configuration
└── static/
    └── index.html                 # Web UI
```

## Using the Web UI

1. Navigate to http://localhost:8080
2. Create products using the Product Management section
3. Add products to cart
4. View and manage your cart
5. Access Swagger documentation for API details

## Cosmos DB Setup

The application will automatically create the following containers in your Cosmos DB database:
- `products` (partition key: `/category`)
- `carts` (partition key: `/userId`)

## Notes

- Default user ID in the UI is `user123`
- Products use auto-generated IDs with timestamp
- Cart is automatically created for new users
- All prices are in USD
- The application uses plain Java POJOs (no Lombok) for Java 25 compatibility

## Troubleshooting

**Issue: Application fails to start with bean definition errors**
- Solution: Ensure you've removed any custom Cosmos DB configuration classes. Spring Boot autoconfiguration handles Cosmos DB setup.

**Issue: Products not loading (ClassCastException)**
- Solution: This was fixed by properly converting `Iterable` to `List` in `ProductService.getAllProducts()`.

**Issue: Containers not auto-created in Cosmos DB**
- Solution: The containers are created on first use when you add a product or create a cart. Check your Cosmos DB connection settings in `application.properties`.
# shopping-cart-service
