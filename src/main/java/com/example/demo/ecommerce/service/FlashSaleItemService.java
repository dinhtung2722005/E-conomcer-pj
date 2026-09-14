package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class FlashSaleItemService {

    @Autowired
    private FlashSaleItemRepository flashSaleItemRepository;
    @Autowired
    private FlashSaleRepository flashSaleRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private FlashSaleItemMapper flashSaleItemMapper;

    @Transactional
    public FlashSaleItemResponse addItemToFlashSale(Long flashSaleId, FlashSaleItemRequest request) {
        FlashSale flashSale = flashSaleRepository.findById(flashSaleId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chương trình Flash Sale!"));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Sản phẩm!"));

        if (flashSaleItemRepository.existsByFlashSaleIdAndProductId(flashSaleId, request.getProductId())) {
            throw new RuntimeException("Sản phẩm này đã tồn tại trong chương trình Flash Sale!");
        }

        if (request.getPromotionalPrice().compareTo(product.getPrice()) >= 0) {
            throw new RuntimeException("Giá Flash Sale phải nhỏ hơn giá gốc của sản phẩm!");
        }

        FlashSaleItem item = FlashSaleItem.builder()
                .flashSale(flashSale)
                .product(product)
                .promotionalPrice(request.getPromotionalPrice())
                .totalQuantity(request.getTotalQuantity())
                .soldQuantity(0)
                .build();

        return flashSaleItemMapper.toResponse(flashSaleItemRepository.save(item));
    }

    // 2. LẤY DANH SÁCH SẢN PHẨM CỦA 1 FLASH SALE
    public List<FlashSaleItemResponse> getItemsByFlashSaleId(Long flashSaleId) {
        return flashSaleItemRepository.findByFlashSaleId(flashSaleId)
                .stream()
                .map(flashSaleItemMapper::toResponse)
                .toList();
    }

    @Transactional
    public FlashSaleItemResponse updateFlashSaleItem(Long itemId, FlashSaleItemRequest request) {
        FlashSaleItem item = flashSaleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Item trong Flash Sale!"));

        if (request.getTotalQuantity() < item.getSoldQuantity()) {
            throw new RuntimeException("Số lượng tổng không được nhỏ hơn số lượng đã bán (" + item.getSoldQuantity() + ")");
        }

        if (!item.getProduct().getId().equals(request.getProductId())) {
            throw new RuntimeException("Không được phép thay đổi Sản phẩm gốc. Vui lòng xóa và tạo Item mới!");
        }

        item.setPromotionalPrice(request.getPromotionalPrice());
        item.setTotalQuantity(request.getTotalQuantity());

        return flashSaleItemMapper.toResponse(flashSaleItemRepository.save(item));
    }

    @Transactional
    public void removeFlashSaleItem(Long itemId) {
        FlashSaleItem item = flashSaleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Item!"));
        
        flashSaleItemRepository.delete(item);
    }
    public BigDecimal getActiveFlashSalePrice(Long productId) {
        return flashSaleItemRepository.findActiveFlashSalePrice(
                productId, 
                FlashSaleStatus.ONGOING, 
                LocalDateTime.now()
        ).orElse(null);
    }
}