package com.shopping.cart.repository;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.shopping.cart.model.Product;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends CosmosRepository<Product, String> {
    List<Product> findByCategory(String category);
}
