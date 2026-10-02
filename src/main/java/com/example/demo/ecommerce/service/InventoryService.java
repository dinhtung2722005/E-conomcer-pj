package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.InventoryRequest;
import com.example.demo.ecommerce.dto.InventoryResponse;
import com.example.demo.ecommerce.entity.Inventory;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.mapper.InventoryMapper;
import com.example.demo.ecommerce.repository.InventoryRepository;
import com.example.demo.ecommerce.repository.ProductRepository;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryMapper inventoryMapper;

    @Transactional
    public InventoryResponse addStock(InventoryRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Sản phẩm!"));

        Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                .orElse(Inventory.builder()
                        .product(product)
                        .availableQuantity(0)
                        .lockedQuantity(0)
                        .build());

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantityToAdd());

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }

    public List<InventoryResponse> getAllInventories() {
        return inventoryRepository.findAll()
                .stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    public InventoryResponse getInventoryByProductId(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm này chưa được khởi tạo kho!"));
        return inventoryMapper.toResponse(inventory);
    }
    @Transactional
    public InventoryResponse releaseInventory(Long productId, Integer quantityToRelease) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm chưa có dữ liệu tồn kho!"));

        if (inventory.getLockedQuantity() < quantityToRelease) {
            throw new RuntimeException("Lỗi dữ liệu: Số lượng hoàn trả (" + quantityToRelease + 
                                       ") lớn hơn số lượng đang tạm giữ (" + inventory.getLockedQuantity() + ")!");
        }

        inventory.setLockedQuantity(inventory.getLockedQuantity() - quantityToRelease);
        
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantityToRelease);

        return inventoryMapper.toResponse(inventoryRepository.save(inventory));
    }
    @Transactional
    public void deductInventory(Long productId, Integer quantityToDeduct) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm chưa có dữ liệu tồn kho!"));

        if (inventory.getAvailableQuantity() < quantityToDeduct) {
            throw new RuntimeException("Số lượng tồn kho khả dụng không đủ!");
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantityToDeduct);
        inventory.setLockedQuantity(inventory.getLockedQuantity() + quantityToDeduct);

        inventoryRepository.save(inventory);
    }
}