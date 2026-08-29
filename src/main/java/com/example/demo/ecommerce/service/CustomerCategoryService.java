package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.CategoryCustomerResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.repository.CategoryRepository;

@Service
public class CustomerCategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<CategoryCustomerResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToCustomerResponse)
                .toList();
    }

    private CategoryCustomerResponse mapToCustomerResponse(Category category) {
        return new CategoryCustomerResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }   
}