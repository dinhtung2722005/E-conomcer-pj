package com.example.demo.ecommerce.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.VoucherRequest;
import com.example.demo.ecommerce.dto.VoucherResponse;
import com.example.demo.ecommerce.entity.DiscountType;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.mapper.VoucherMapper;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.repository.VoucherRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private static final BigDecimal MAX_PERCENTAGE = BigDecimal.valueOf(100);

    private final VoucherRepository voucherRepository;
    private final OrderRepository orderRepository;
    private final VoucherMapper voucherMapper;

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        String code = normalizeCode(request.getCode());

        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTED);
        }
        validateConfig(request);

        Voucher voucher = Voucher.builder()
                .code(code)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderValue(request.getMinOrderValue())
                .totalQuantity(request.getTotalQuantity())
                .usedQuantity(0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        return voucherMapper.toVoucherResponse(voucherRepository.save(voucher));
    }

    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> getAllVouchers(PageQuery pagination) {
        return PageResponse.from(voucherRepository.findAll(pagination.toPageable())
                .map(voucherMapper::toVoucherResponse));
    }

    @Transactional(readOnly = true)
    public VoucherResponse getVoucherById(Long id) {
        return voucherMapper.toVoucherResponse(findVoucher(id));
    }

    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = findVoucher(id);
        String code = normalizeCode(request.getCode());

        if (!code.equalsIgnoreCase(voucher.getCode()) && voucherRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTED);
        }
        validateConfig(request);

        if (request.getTotalQuantity() < voucher.getUsedQuantity()) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_CONFIG,
                    "Tổng số lượng không được nhỏ hơn số lượt đã dùng (" + voucher.getUsedQuantity() + ")");
        }

        voucher.setCode(code);
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMaxDiscountAmount(request.getMaxDiscountAmount());
        voucher.setMinOrderValue(request.getMinOrderValue());
        voucher.setTotalQuantity(request.getTotalQuantity());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());

        return voucherMapper.toVoucherResponse(voucherRepository.save(voucher));
    }

    /** Không cho xóa voucher đã gắn với đơn hàng (sẽ vi phạm khóa ngoại và mất dấu vết). */
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = findVoucher(id);

        if (orderRepository.existsByVoucherId(id)) {
            throw new AppException(ErrorCode.VOUCHER_IN_USE,
                    "Mã '" + voucher.getCode() + "' đã được dùng trong đơn hàng, không thể xóa");
        }
        if (voucher.getUsedQuantity() != null && voucher.getUsedQuantity() > 0) {
            throw new AppException(ErrorCode.VOUCHER_IN_USE);
        }

        voucherRepository.delete(voucher);
    }

    /** Kiểm tra cấu hình voucher có tự mâu thuẫn không. */
    private void validateConfig(VoucherRequest request) {
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        if (request.getDiscountType() == DiscountType.PERCENTAGE
                && request.getDiscountValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new AppException(ErrorCode.VOUCHER_INVALID_CONFIG,
                    "Voucher giảm theo phần trăm không được vượt quá 100%");
        }

        if (request.getMaxDiscountAmount() != null && request.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            // Không sai về mặt kỹ thuật nhưng gây nhầm lẫn — cảnh báo bằng lỗi để admin sửa lại.
            throw new AppException(ErrorCode.VOUCHER_INVALID_CONFIG,
                    "Voucher giảm số tiền cố định không cần nhập mức giảm tối đa");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private Voucher findVoucher(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }
}
