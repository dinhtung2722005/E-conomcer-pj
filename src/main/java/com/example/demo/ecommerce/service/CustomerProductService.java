package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // Giả sử bạn có Enum này (ACTIVE, INACTIVE)
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.ProductCustomerResponse;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.ProductStatus;
import com.example.demo.ecommerce.repository.InventoryRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.user.dto_request.PageResponse;

@Service
public class CustomerProductService {

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    // @Autowired
    // private FlashSaleItemService flashSaleItemService; // Sẽ dùng sau để check giá Flash Sale

    
   public PageResponse<ProductCustomerResponse> getAllActiveProducts(
            int pageNo, int pageSize, String sortBy, String sortDir, Long categoryId) {
        
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) 
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Page<Product> productPage;
        if (categoryId != null) {
           productPage = productRepository.findByCategoriesIdAndStatus(categoryId, ProductStatus.ACTIVE, pageable);
        } else {
            productPage = productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
        }

        List<ProductCustomerResponse> content = productPage.getContent().stream()
                .map(this::mapToCustomerResponse)
                .toList();

        // Sử dụng @Builder từ class PageResponse của bạn
        return PageResponse.<ProductCustomerResponse>builder()
                .currentPage(productPage.getNumber())
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .data(content)
                .build();
    }
    public ProductCustomerResponse getProductDetail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm!"));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new RuntimeException("Sản phẩm đã ngừng kinh doanh!");
        }

        return mapToCustomerResponse(product);
    }

    private ProductCustomerResponse mapToCustomerResponse(Product product) {
        Integer availableQty = inventoryRepository.findByProductId(product.getId())
                .map(inv -> inv.getAvailableQuantity())
                .orElse(0);
        BigDecimal flashSalePrice = null; 
       String categoryNames = product.getCategories().stream()
                .map(category -> category.getName())
                .collect(java.util.stream.Collectors.joining(", "));
        return new ProductCustomerResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                flashSalePrice,
                categoryNames, 
                availableQty
        );
    }
}