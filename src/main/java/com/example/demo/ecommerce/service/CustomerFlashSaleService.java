package com.example.demo.ecommerce.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.FlashSaleCustomerResponse;
import com.example.demo.ecommerce.dto.FlashSaleItemCustomerResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleStatus;
import com.example.demo.ecommerce.repository.FlashSaleRepository;

@Service
public class CustomerFlashSaleService {

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    public FlashSaleCustomerResponse getCurrentFlashSale() {
        LocalDateTime now = LocalDateTime.now();
        
        
        FlashSale currentSale = flashSaleRepository.findFirstByStatusAndStartTimeBeforeAndEndTimeAfter(
                FlashSaleStatus.ONGOING, now, now).orElse(null);

        if (currentSale == null) {
            return null; 
        }

        return mapToCustomerResponse(currentSale);
    }

    private FlashSaleCustomerResponse mapToCustomerResponse(FlashSale flashSale) {
        List<FlashSaleItemCustomerResponse> itemResponses = flashSale.getItems().stream()
                .map(item -> new FlashSaleItemCustomerResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getPromotionalPrice(), 
                        item.getSoldQuantity(),  // Giả sử có trường này để đếm số đã bán
                        item.getTotalQuantity()       // Tổng số lượng cho phép bán trong Flash Sale
                ))
                .toList();

        return new FlashSaleCustomerResponse(
                flashSale.getId(),
                flashSale.getName(),
                flashSale.getStartTime(),
                flashSale.getEndTime(),
                itemResponses
        );
    }
}