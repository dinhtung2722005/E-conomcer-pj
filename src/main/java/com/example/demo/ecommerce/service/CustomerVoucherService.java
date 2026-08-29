package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.VoucherCustomerResponse;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.repository.VoucherRepository;

@Service
public class CustomerVoucherService {

    @Autowired
    private VoucherRepository voucherRepository;

    public List<VoucherCustomerResponse> getAvailableVouchers() {
        List<Voucher> vouchers = voucherRepository.findAvailableVouchers(LocalDateTime.now());
        return vouchers.stream()
                .map(this::mapToCustomerResponse)
                .toList();
    }

    public VoucherCustomerResponse validateVoucher(String code, BigDecimal orderTotalAmount) {
        LocalDateTime now = LocalDateTime.now();
        
        Voucher voucher = voucherRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Mã giảm giá không tồn tại!"));

        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new RuntimeException("Mã giảm giá đã hết hạn hoặc chưa đến thời gian sử dụng!");
        }

        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new RuntimeException("Mã giảm giá đã được sử dụng hết!");
        }

        if (orderTotalAmount.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new RuntimeException("Đơn hàng chưa đạt giá trị tối thiểu để sử dụng mã này!");
        }

        return mapToCustomerResponse(voucher);
    }

    private VoucherCustomerResponse mapToCustomerResponse(Voucher voucher) {
        
        String description = "";
        if ("PERCENT".equalsIgnoreCase(voucher.getDiscountType().name())) {
            description = "Giảm " + voucher.getDiscountValue() + "%";
            if (voucher.getMaxDiscountAmount() != null) {
                description += " (Tối đa " + voucher.getMaxDiscountAmount() + ")";
            }
        } else {
            description = "Giảm " + voucher.getDiscountValue() + "đ";
        }
        description += " cho đơn từ " + voucher.getMinOrderValue();

        return new VoucherCustomerResponse(
                voucher.getId(),
                voucher.getCode(),
                description, 
                voucher.getDiscountType().name(), 
                voucher.getDiscountValue(),
                voucher.getMaxDiscountAmount(),
                voucher.getMinOrderValue(),
                voucher.getEndDate() 
        );
    }
}