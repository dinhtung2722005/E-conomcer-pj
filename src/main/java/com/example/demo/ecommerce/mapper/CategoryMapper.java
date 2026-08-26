package com.example.demo.ecommerce.mapper;

import org.mapstruct.Mapper;

import com.example.demo.ecommerce.dto.CategoryResponse;
import com.example.demo.ecommerce.entity.Category;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    
    CategoryResponse toCategoryResponse(Category category);
}