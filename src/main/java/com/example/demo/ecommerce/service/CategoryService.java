package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.CategoryRequest;
import com.example.demo.ecommerce.dto.CategoryResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.mapper.CategoryMapper;
import com.example.demo.ecommerce.repository.CategoryRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import jakarta.transaction.Transactional;
@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    public CategoryService(CategoryRepository categoryRepository,CategoryMapper categoryMapper ){
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }
    public CategoryResponse create(CategoryRequest request){
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        Category savedCategory = categoryRepository.save(category);
        return categoryMapper.toCategoryResponse(savedCategory);
    }
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(categoryMapper::toCategoryResponse)
                .toList();
    }
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục có ID: " + id));
        return categoryMapper.toCategoryResponse(category);
    }
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Category_Not_Found));

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);
        return categoryMapper.toCategoryResponse(updatedCategory);
    }
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Category_Not_Found));
        categoryRepository.delete(category);
    }
}

