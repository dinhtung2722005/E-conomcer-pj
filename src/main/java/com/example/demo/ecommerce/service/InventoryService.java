package com.example.demo.ecommerce.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.InventoryRequest;
import com.example.demo.ecommerce.dto.InventoryResponse;
import com.example.demo.ecommerce.entity.Inventory;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.mapper.InventoryMapper;
import com.example.demo.ecommerce.repository.InventoryRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý tồn kho theo mô hình hai số dư:
 * <ul>
 *   <li>{@code availableQuantity} — số bán được ngay;</li>
 *   <li>{@code lockedQuantity} — số đã bị đơn hàng giữ chỗ nhưng chưa hoàn tất.</li>
 * </ul>
 *
 * <p>Vòng đời: đặt hàng → {@link #deductInventory} (available → locked);
 * đơn được thanh toán → {@link #confirmSale} (trừ hẳn locked);
 * đơn bị hủy → {@link #releaseInventory} (locked → available).
 *
 * <p>Mọi thao tác ghi đều khóa bi quan dòng tồn kho nên hai đơn đồng thời
 * được xử lý tuần tự, không bán lố.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final InventoryMapper inventoryMapper;

    @Transactional
    public InventoryResponse addStock(InventoryRequest request) {
        if (request.getQuantityToAdd() == null || request.getQuantityToAdd() <= 0) {
            throw new AppException(ErrorCode.INVALID_QUANTITY);
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(request.getProductId())
                .orElseGet(() -> Inventory.builder()
                        .product(product)
                        .availableQuantity(0)
                        .lockedQuantity(0)
                        .build());

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantityToAdd());
        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventories() {
        return inventoryRepository.findAll().stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));
        return inventoryMapper.toResponse(inventory);
    }

    /** Giữ chỗ hàng cho một đơn vừa đặt. */
    @Transactional
    public void deductInventory(Long productId, Integer quantity) {
        checkQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new AppException(ErrorCode.OUT_OF_STOCK,
                    "Sản phẩm ID " + productId + " chỉ còn " + inventory.getAvailableQuantity() + " trong kho");
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventory.setLockedQuantity(inventory.getLockedQuantity() + quantity);
        inventoryRepository.save(inventory);
    }

    /** Đơn đã thanh toán: hàng rời kho vĩnh viễn, không quay lại {@code available}. */
    @Transactional
    public void confirmSale(Long productId, Integer quantity) {
        checkQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> inconsistent(productId, quantity, 0));

        if (inventory.getLockedQuantity() < quantity) {
            throw inconsistent(productId, quantity, inventory.getLockedQuantity());
        }

        inventory.setLockedQuantity(inventory.getLockedQuantity() - quantity);
        inventoryRepository.save(inventory);
    }

    /** Đơn bị hủy: trả hàng đang giữ chỗ về trạng thái bán được. */
    @Transactional
    public void releaseInventory(Long productId, Integer quantity) {
        checkQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> inconsistent(productId, quantity, 0));

        if (inventory.getLockedQuantity() < quantity) {
            throw inconsistent(productId, quantity, inventory.getLockedQuantity());
        }

        inventory.setLockedQuantity(inventory.getLockedQuantity() - quantity);
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventoryRepository.save(inventory);
    }

    /**
     * Trả hàng về kho khi đơn đã thanh toán bị hủy (hoàn tiền).
     * Khác {@link #releaseInventory}: hàng đã rời {@code locked} trước đó nên chỉ cộng lại {@code available}.
     */
    @Transactional
    public void restock(Long productId, Integer quantity) {
        checkQuantity(quantity);

        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventoryRepository.save(inventory);
    }

    /** Số lượng khả dụng của nhiều sản phẩm trong một truy vấn. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> getAvailableQuantities(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findByProductIdIn(productIds)) {
            result.put(inventory.getProduct().getId(), inventory.getAvailableQuantity());
        }
        return result;
    }

    private AppException inconsistent(Long productId, Integer needed, Integer locked) {
        return new AppException(ErrorCode.INVENTORY_INCONSISTENT,
                "Dữ liệu tồn kho không nhất quán cho sản phẩm ID " + productId
                        + " (cần " + needed + ", đang giữ " + locked + ")");
    }

    private void checkQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new AppException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
