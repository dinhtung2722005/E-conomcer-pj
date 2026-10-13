package com.example.demo.ecommerce.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.FlashSaleCustomerResponse;
import com.example.demo.ecommerce.dto.FlashSaleItemCustomerResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleStatus;
import com.example.demo.ecommerce.repository.FlashSaleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerFlashSaleService {

    private final FlashSaleRepository flashSaleRepository;

    /** Chương trình đang chạy, hoặc {@code null} nếu hiện không có. */
    @Transactional(readOnly = true)
    public FlashSaleCustomerResponse getCurrentFlashSale() {
        LocalDateTime now = LocalDateTime.now();

        FlashSale currentSale = flashSaleRepository
                .findFirstByStatusAndStartTimeBeforeAndEndTimeAfter(FlashSaleStatus.ONGOING, now, now)
                .orElse(null);

        if (currentSale == null) {
            return null;
        }

        List<FlashSaleItemCustomerResponse> items = currentSale.getItems().stream()
                .map(item -> new FlashSaleItemCustomerResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getPromotionalPrice(),
                        item.getSoldQuantity(),
                        item.getTotalQuantity()))
                .toList();

        return new FlashSaleCustomerResponse(
                currentSale.getId(),
                currentSale.getName(),
                currentSale.getStartTime(),
                currentSale.getEndTime(),
                items);
    }
}
