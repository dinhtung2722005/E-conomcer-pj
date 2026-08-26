package com.example.demo.ecommerce.mapper;

import com.example.demo.ecommerce.dto.ProductResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(source = "categories", target = "categoryNames", qualifiedByName = "mapCategoriesToNames")
    ProductResponse toProductResponse(Product product);

    @Named("mapCategoriesToNames")
    default Set<String> mapCategoriesToNames(Set<Category> categories) {
        if (categories == null) {
            return null;
        }
        return categories.stream()
                .map(Category::getName)
                .collect(Collectors.toSet());
    }
}