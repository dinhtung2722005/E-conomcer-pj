package com.example.demo.ecommerce.controller;
import com.example.demo.ecommerce.api.dto.CategoryRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.api.dto.VoucherRequest;
import com.example.demo.ecommerce.api.dto.VoucherResponse;
import com.example.demo.ecommerce.service.VoucherService;
import com.example.demo.shared.web.ApiResponse;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VoucherResponse> createVoucher(@Valid @RequestBody VoucherRequest request) {
        return new ApiResponse<>(201, "Tạo mã giảm giá thành công", voucherService.createVoucher(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<VoucherResponse>> getAllVouchers(@Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công", voucherService.getAllVouchers(pagination));
    }

    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> getVoucherById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", voucherService.getVoucherById(id));
    }

    /**
     * Cập nhật voucher.
     *
     * <p>Bản cũ nhận {@code @RequestBody CategoryRequest} (sai kiểu) và chỉ gọi
     * {@code getVoucherById} — endpoint hoàn toàn không cập nhật gì cả.
     */
    @PutMapping("/{id}")
    public ApiResponse<VoucherResponse> updateVoucher(@PathVariable Long id,
                                                      @Valid @RequestBody VoucherRequest request) {
        return new ApiResponse<>(200, "Cập nhật mã giảm giá thành công", voucherService.updateVoucher(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return new ApiResponse<>(200, "Xóa mã giảm giá thành công", null);
    }
}
