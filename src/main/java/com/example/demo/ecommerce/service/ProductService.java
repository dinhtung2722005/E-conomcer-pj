package com.example.demo.ecommerce.service;
import com.example.demo.user.api.UserAccount;
import com.example.demo.user.api.UserRole;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.api.dto.ProductRequest;
import com.example.demo.ecommerce.api.dto.ProductResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.api.ProductStatus;
import com.example.demo.ecommerce.mapper.ProductMapper;
import com.example.demo.ecommerce.repository.CategoryRepository;
import com.example.demo.ecommerce.repository.OrderItemRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductMapper productMapper;
    private final com.example.demo.user.api.UserAccountsApi users;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        String name = request.getName().trim();
        if (productRepository.existsByName(name)) {
            throw new AppException(ErrorCode.PRODUCT_NAME_EXISTED);
        }

        Product product = Product.builder()
                .name(name)
                .sellerId(validateSeller(request.getSellerId()))
                .description(request.getDescription())
                .price(request.getPrice())
                .status(request.getStatus())
                .categories(resolveCategories(request.getCategoryIds()))
                .build();

        return productMapper.toProductResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllProducts(PageQuery pagination) {
        return PageResponse.from(productRepository.findAll(pagination.toPageable())
                .map(productMapper::toProductResponse));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return productMapper.toProductResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        String name = request.getName().trim();

        if (productRepository.existsByNameAndIdNot(name, id)) {
            throw new AppException(ErrorCode.PRODUCT_NAME_EXISTED);
        }

        product.setName(name);
        if (request.getSellerId() != null) product.setSellerId(validateSeller(request.getSellerId()));
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStatus(request.getStatus());
        product.setCategories(resolveCategories(request.getCategoryIds()));

        return productMapper.toProductResponse(productRepository.save(product));
    }

    /**
     * Ngừng kinh doanh sản phẩm.
     *
     * <p>Sản phẩm đã phát sinh đơn hàng <b>không</b> bị xóa cứng: xóa sẽ phá vỡ lịch sử
     * mua hàng và vi phạm khóa ngoại từ {@code order_items}. Thay vào đó chuyển sang
     * trạng thái {@link ProductStatus#INACTIVE} — khách không mua được nữa nhưng hóa
     * đơn cũ vẫn nguyên vẹn.
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);

        if (orderItemRepository.existsByProductId(id)) {
            product.setStatus(ProductStatus.INACTIVE);
            productRepository.save(product);
            return;
        }

        productRepository.delete(product);
    }

    private Long validateSeller(Long id) {
        if (id == null) return null;
        return users.findById(id).filter(user -> user.isActive()
                && user.getRole() == com.example.demo.user.api.UserRole.SELLER)
                .map(com.example.demo.user.api.UserAccount::getId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Người bán phải là tài khoản SELLER đang hoạt động"));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /** Danh mục không tồn tại phải báo lỗi, không được âm thầm bỏ qua. */
    private Set<Category> resolveCategories(Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Category> categories = categoryRepository.findAllById(categoryIds);
        if (categories.size() != categoryIds.size()) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND,
                    "Một hoặc nhiều danh mục không tồn tại");
        }
        return new HashSet<>(categories);
    }
}
