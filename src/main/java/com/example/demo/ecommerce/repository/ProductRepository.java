package com.example.demo.ecommerce.repository;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.ProductStatus;
public interface  ProductRepository extends JpaRepository<Product,Long> {
         Optional<Product> findByName(String name);
         Optional<Product> findByStatus(String status);
         Page<Product> findByStatus(ProductStatus status, Pageable pageable);

         Page<Product> findByCategoriesIdAndStatus(Long categoryId, ProductStatus status, Pageable pageable);
}
