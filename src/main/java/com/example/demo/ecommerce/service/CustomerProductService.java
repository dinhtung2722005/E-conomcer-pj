package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.ProductCustomerResponse;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.ProductStatus;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerProductService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "price", "createdAt");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final FlashSaleItemService flashSaleItemService;

    @Transactional(readOnly = true)
    public PageResponse<ProductCustomerResponse> getAllActiveProducts(
            int pageNo, int pageSize, String sortBy, String sortDir, Long categoryId) {

        int safePage = Math.max(pageNo, 0);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        Sort sort = Sort.by(sortDir != null && sortDir.equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC,
                ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id");

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<Product> productPage = categoryId != null
                ? productRepository.findByCategoriesIdAndStatus(categoryId, ProductStatus.ACTIVE, pageable)
                : productRepository.findByStatus(ProductStatus.ACTIVE, pageable);

        List<ProductCustomerResponse> content = mapAll(productPage.getContent());

        return PageResponse.<ProductCustomerResponse>builder()
                .currentPage(productPage.getNumber())
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .data(content)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductCustomerResponse getProductDetail(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
        }

        return mapAll(List.of(product)).get(0);
    }

    /**
     * Ánh xạ cả trang sản phẩm với đúng <b>hai</b> truy vấn phụ (tồn kho và giá sốc)
     * thay vì hai truy vấn cho mỗi sản phẩm như trước.
     */
    private List<ProductCustomerResponse> mapAll(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, Integer> availableQuantities = inventoryService.getAvailableQuantities(productIds);
        Map<Long, BigDecimal> flashSalePrices = flashSaleItemService.getActiveFlashSalePrices(productIds);

        return products.stream()
                .map(product -> new ProductCustomerResponse(
                        product.getId(),
                        product.getName(),
                        product.getDescription(),
                        product.getPrice(),
                        flashSalePrices.get(product.getId()),
                        product.getCategories().stream()
                                .map(category -> category.getName())
                                .sorted()
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(null),
                        availableQuantities.getOrDefault(product.getId(), 0)))
                .toList();
    }
}
