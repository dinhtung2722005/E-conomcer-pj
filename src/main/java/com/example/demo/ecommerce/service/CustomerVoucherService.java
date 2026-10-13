package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.VoucherCustomerResponse;
import com.example.demo.ecommerce.entity.DiscountType;
import com.example.demo.ecommerce.entity.UserVoucher;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.repository.UserVoucherRepository;
import com.example.demo.ecommerce.repository.VoucherRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/** Danh sách và ví voucher của khách hàng. */
@Service
@RequiredArgsConstructor
public class CustomerVoucherService {

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    @Transactional(readOnly = true)
    public List<VoucherCustomerResponse> getAvailableVouchers() {
        return voucherRepository.findAvailableVouchers(LocalDateTime.now()).stream()
                .map(CustomerVoucherService::toCustomerResponse)
                .toList();
    }

    /** Lưu voucher vào ví. Phải nằm trong transaction vì đây là thao tác kiểm tra rồi ghi. */
    @Transactional
    public void saveVoucherToWallet(Long userId, String code) {
        Voucher voucher = voucherRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        if (userVoucherRepository.existsByUserIdAndVoucherId(userId, voucher.getId())) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_SAVED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT);
        }

        userVoucherRepository.save(UserVoucher.builder()
                .userId(userId)
                .voucher(voucher)
                .isUsed(false)
                .build());
    }

    @Transactional(readOnly = true)
    public List<VoucherCustomerResponse> getMyVouchers(Long userId) {
        return userVoucherRepository.findByUserIdAndIsUsedFalse(userId).stream()
                .map(userVoucher -> toCustomerResponse(userVoucher.getVoucher()))
                .toList();
    }

    /** Kiểm tra voucher có dùng được cho một giá trị đơn hàng hay không (không tiêu lượt). */
    @Transactional(readOnly = true)
    public VoucherCustomerResponse validateVoucher(String code, BigDecimal orderTotalAmount) {
        Voucher voucher = voucherRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT);
        }
        if (orderTotalAmount == null || orderTotalAmount.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER);
        }

        return toCustomerResponse(voucher);
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    /**
     * Tạo mô tả dễ đọc cho khách.
     *
     * <p>Bản cũ so sánh với {@code "PERCENT"} trong khi enum là {@code PERCENTAGE},
     * nên mọi voucher phần trăm đều bị hiển thị sai thành "Giảm 30đ".
     */
    private static VoucherCustomerResponse toCustomerResponse(Voucher voucher) {
        StringBuilder description = new StringBuilder("Giảm ");

        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            description.append(trimTrailingZeros(voucher.getDiscountValue())).append('%');
            if (voucher.getMaxDiscountAmount() != null) {
                description.append(" (tối đa ").append(trimTrailingZeros(voucher.getMaxDiscountAmount())).append(')');
            }
        } else {
            description.append(trimTrailingZeros(voucher.getDiscountValue())).append('đ');
        }

        description.append(" cho đơn từ ").append(trimTrailingZeros(voucher.getMinOrderValue()));

        return new VoucherCustomerResponse(
                voucher.getId(),
                voucher.getCode(),
                description.toString(),
                voucher.getDiscountType().name(),
                voucher.getDiscountValue(),
                voucher.getMaxDiscountAmount(),
                voucher.getMinOrderValue(),
                voucher.getEndDate());
    }

    private static String trimTrailingZeros(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
