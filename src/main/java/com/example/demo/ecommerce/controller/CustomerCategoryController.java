package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.CategoryCustomerResponse;
import com.example.demo.ecommerce.service.CustomerCategoryService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/categories") 
public class CustomerCategoryController {

    @Autowired
    private CustomerCategoryService customerCategoryService;

    @GetMapping
    public ApiResponse<List<CategoryCustomerResponse>> getAllCategories() {
        return new ApiResponse<>(200, "Thành công", customerCategoryService.getAllCategories());
    }
}