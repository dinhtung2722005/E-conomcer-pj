package com.example.demo.ecommerce.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.ProductRequest;
import com.example.demo.ecommerce.dto.ProductResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.mapper.ProductMapper;
import com.example.demo.ecommerce.repository.CategoryRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    public ProductService(ProductRepository productRepository,CategoryRepository categoryRepository,ProductMapper productMapper ){
        this.categoryRepository=categoryRepository;
        this.productRepository = productRepository;
        this.productMapper=productMapper;
    }
    @Transactional
    public ProductResponse createProduct (ProductRequest request){
        List<Category> categoryList = categoryRepository.findAllById(request.getCategoryIds());
        Set<Category> categories = new HashSet<>(categoryList);
          Product product = new Product();
          product.setName(request.getName());
          product.setDescription(request.getDescription());
          product.setPrice(request.getPrice());
          product.setStatus(request.getStatus());
          product.setCategories(categories);  
          productRepository.save(product);
          return productMapper.toProductResponse(product);
    }
    
   public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(productMapper::toProductResponse) 
                .toList();
    }
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request){
            Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Product_Not_Found));
            List<Category> categoryList = categoryRepository.findAllById(request.getCategoryIds());
            Set<Category> categories = new HashSet<>(categoryList);
            product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStatus(request.getStatus());
        product.setCategories(categories);

        Product updatedProduct = productRepository.save(product);
        return productMapper.toProductResponse(updatedProduct);
    }
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Product_Not_Found));
                productRepository.delete(product);
    }
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Product_Not_Found));
        return productMapper.toProductResponse(product);
    }
    
    
}
