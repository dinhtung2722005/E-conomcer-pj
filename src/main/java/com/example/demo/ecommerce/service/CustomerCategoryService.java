package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.api.dto.CategoryCustomerResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerCategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryCustomerResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CustomerCategoryService::mapToCustomerResponse)
                .toList();
    }

    private static CategoryCustomerResponse mapToCustomerResponse(Category category) {
        return new CategoryCustomerResponse(
                category.getId(),
                category.getName(),
                category.getDescription());
    }
}
