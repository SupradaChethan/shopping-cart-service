package com.shopping.cart.repository;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.shopping.cart.model.Cart;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends CosmosRepository<Cart, String> {
    Optional<Cart> findByUserId(String userId);
}
