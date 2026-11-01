package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.api.dto.CategoryRequest;
import com.example.demo.ecommerce.api.dto.CategoryResponse;
import com.example.demo.ecommerce.entity.Category;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.mapper.CategoryMapper;
import com.example.demo.ecommerce.repository.CategoryRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.DATA_CONFLICT, "Danh mục '" + name + "' đã tồn tại");
        }

        Category category = Category.builder()
                .name(name)
                .description(request.getDescription())
                .build();

        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> getAllCategories(PageQuery pagination) {
        return PageResponse.from(categoryRepository.findAll(pagination.toPageable())
                .map(categoryMapper::toCategoryResponse));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return categoryMapper.toCategoryResponse(findCategory(id));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String name = request.getName().trim();

        if (!name.equalsIgnoreCase(category.getName()) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.DATA_CONFLICT, "Danh mục '" + name + "' đã tồn tại");
        }

        category.setName(name);
        category.setDescription(request.getDescription());
        return categoryMapper.toCategoryResponse(categoryRepository.save(category));
    }

    /**
     * Xóa danh mục.
     *
     * <p>Phải gỡ liên kết với sản phẩm trước: bảng trung gian {@code product_categories}
     * không có cascade nên xóa trực tiếp sẽ vi phạm khóa ngoại và trả về HTTP 500.
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategory(id);

        List<Product> products = productRepository.findByCategoriesId(id);
        for (Product product : products) {
            product.getCategories().removeIf(c -> c.getId().equals(id));
        }
        productRepository.saveAll(products);

        categoryRepository.delete(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
