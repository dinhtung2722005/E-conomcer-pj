package com.example.demo.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.ecommerce.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}