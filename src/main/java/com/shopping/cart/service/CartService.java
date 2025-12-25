package com.shopping.cart.service;

import com.shopping.cart.model.Cart;
import com.shopping.cart.model.CartItem;
import com.shopping.cart.model.Product;
import com.shopping.cart.repository.CartRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;

    public CartService(CartRepository cartRepository, ProductService productService) {
        this.cartRepository = cartRepository;
        this.productService = productService;
    }

    public Cart getOrCreateCart(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setId(UUID.randomUUID().toString());
                    newCart.setUserId(userId);
                    return cartRepository.save(newCart);
                });
    }

    public Cart addItemToCart(String userId, String productId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);
        Product product = productService.getProductById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(existingItem.get().getQuantity() + quantity);
        } else {
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

    public Cart updateCartItemQuantity(String userId, String productId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);

        cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst()
                .ifPresent(item -> item.setQuantity(quantity));

        return cartRepository.save(cart);
    }

    public Cart removeItemFromCart(String userId, String productId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        return cartRepository.save(cart);
    }

    public void clearCart(String userId) {
        Cart cart = getOrCreateCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public Cart getCart(String userId) {
        return getOrCreateCart(userId);
    }
}
