package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.FlashSaleItemRequest;
import com.example.demo.ecommerce.dto.FlashSaleItemResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleItem;
import com.example.demo.ecommerce.entity.FlashSaleStatus;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.mapper.FlashSaleItemMapper;
import com.example.demo.ecommerce.repository.FlashSaleItemRepository;
import com.example.demo.ecommerce.repository.FlashSaleRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlashSaleItemService {

    private final FlashSaleItemRepository flashSaleItemRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final ProductRepository productRepository;
    private final FlashSaleItemMapper flashSaleItemMapper;
    private final InventoryService inventoryService;

    @Transactional
    public FlashSaleItemResponse addItemToFlashSale(Long flashSaleId, FlashSaleItemRequest request) {
        FlashSale flashSale = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_NOT_FOUND));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (flashSaleItemRepository.existsByFlashSaleIdAndProductId(flashSaleId, request.getProductId())) {
            throw new AppException(ErrorCode.FLASH_SALE_ITEM_EXISTED);
        }

        validatePromotionalPrice(request.getPromotionalPrice(), product);
        validateQuantity(request.getTotalQuantity());
        validateAgainstStock(product, request.getTotalQuantity());

        FlashSaleItem item = FlashSaleItem.builder()
                .flashSale(flashSale)
                .product(product)
                .promotionalPrice(request.getPromotionalPrice())
                .totalQuantity(request.getTotalQuantity())
                .soldQuantity(0)
                .build();

        return flashSaleItemMapper.toResponse(flashSaleItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public PageResponse<FlashSaleItemResponse> getItemsByFlashSaleId(Long flashSaleId, PageQuery pagination) {
        if (!flashSaleRepository.existsById(flashSaleId)) {
            throw new AppException(ErrorCode.FLASH_SALE_NOT_FOUND);
        }
        return PageResponse.from(flashSaleItemRepository.findByFlashSaleId(flashSaleId, pagination.toPageable())
                .map(flashSaleItemMapper::toResponse));
    }

    @Transactional
    public FlashSaleItemResponse updateFlashSaleItem(Long itemId, FlashSaleItemRequest request) {
        FlashSaleItem item = flashSaleItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));

        if (request.getTotalQuantity() < item.getSoldQuantity()) {
            throw new AppException(ErrorCode.FLASH_SALE_QUANTITY_INVALID,
                    "Số lượng tổng không được nhỏ hơn số lượng đã bán (" + item.getSoldQuantity() + ")");
        }

        if (request.getProductId() != null && !item.getProduct().getId().equals(request.getProductId())) {
            throw new AppException(ErrorCode.FLASH_SALE_PRODUCT_IMMUTABLE);
        }

        // Trước đây chỉ kiểm tra giá khi THÊM mới; khi SỬA giá có thể bị đẩy lên
        // cao hơn giá gốc. Kiểm tra lại ở đây.
        validatePromotionalPrice(request.getPromotionalPrice(), item.getProduct());
        validateQuantity(request.getTotalQuantity());
        validateAgainstStock(item.getProduct(), request.getTotalQuantity());

        item.setPromotionalPrice(request.getPromotionalPrice());
        item.setTotalQuantity(request.getTotalQuantity());

        return flashSaleItemMapper.toResponse(flashSaleItemRepository.save(item));
    }

    @Transactional
    public void removeFlashSaleItem(Long itemId) {
        FlashSaleItem item = flashSaleItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));
        flashSaleItemRepository.delete(item);
    }

    /** Giá sốc hiện hành của một sản phẩm, hoặc {@code null} nếu không có suất nào còn hiệu lực. */
    @Transactional(readOnly = true)
    public BigDecimal getActiveFlashSalePrice(Long productId) {
        return findActiveItem(productId).map(item -> item.getPromotionalPrice()).orElse(null);
    }

    /** Giá sốc hiện hành của nhiều sản phẩm — một truy vấn duy nhất. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getActiveFlashSalePrices(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        List<FlashSaleItem> items = flashSaleItemRepository.findActiveItemsByProductIds(
                productIds, FlashSaleStatus.ONGOING, LocalDateTime.now());
        for (FlashSaleItem item : items) {
            // Nếu một sản phẩm nằm trong nhiều chương trình, giữ giá thấp nhất.
            result.merge(item.getProduct().getId(), item.getPromotionalPrice(),
                    (currentPrice, newPrice) -> currentPrice.min(newPrice));
        }
        return result;
    }

    /**
     * Giữ chỗ {@code quantity} suất flash sale cho sản phẩm và trả về đơn giá áp dụng.
     *
     * <p>Trả về giá sốc nếu suất còn đủ cho toàn bộ số lượng yêu cầu; nếu suất đã hết
     * hoặc không đủ, trả về giá gốc (khách vẫn mua được hàng, chỉ mất ưu đãi).
     * Việc khóa dòng + tăng {@code soldQuantity} đảm bảo không bán vượt hạn mức.
     */
    @Transactional
    public BigDecimal reserveFlashSaleItem(Product product, Integer quantity) {
        Optional<FlashSaleItem> activeItem = flashSaleItemRepository.findActiveItemForUpdate(
                product.getId(), FlashSaleStatus.ONGOING, LocalDateTime.now());

        if (activeItem.isEmpty()) {
            return product.getPrice();
        }

        FlashSaleItem item = activeItem.get();
        int remaining = item.getTotalQuantity() - item.getSoldQuantity();
        if (remaining < quantity) {
            return product.getPrice();
        }

        item.setSoldQuantity(item.getSoldQuantity() + quantity);
        flashSaleItemRepository.save(item);
        return item.getPromotionalPrice();
    }

    private Optional<FlashSaleItem> findActiveItem(Long productId) {
        return flashSaleItemRepository.findActiveItem(productId, FlashSaleStatus.ONGOING, LocalDateTime.now());
    }

    private void validatePromotionalPrice(BigDecimal promotionalPrice, Product product) {
        if (promotionalPrice == null || promotionalPrice.compareTo(BigDecimal.ZERO) <= 0
                || promotionalPrice.compareTo(product.getPrice()) >= 0) {
            throw new AppException(ErrorCode.FLASH_SALE_PRICE_INVALID);
        }
    }

    private void validateQuantity(Integer totalQuantity) {
        if (totalQuantity == null || totalQuantity <= 0) {
            throw new AppException(ErrorCode.FLASH_SALE_QUANTITY_INVALID);
        }
    }

    /** Không cho cam kết bán nhiều hơn số hàng đang thực có. */
    private void validateAgainstStock(Product product, Integer totalQuantity) {
        Integer available = inventoryService.getAvailableQuantities(List.of(product.getId()))
                .get(product.getId());
        if (available != null && totalQuantity > available) {
            throw new AppException(ErrorCode.FLASH_SALE_EXCEEDS_STOCK,
                    "Số lượng khuyến mãi (" + totalQuantity + ") vượt quá tồn kho khả dụng (" + available + ")");
        }
    }
}
