package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.VoucherRequest;
import com.example.demo.ecommerce.dto.VoucherResponse;
import com.example.demo.ecommerce.service.VoucherService;
import com.example.demo.user.dto_request.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @PostMapping
    public ApiResponse<VoucherResponse> createVoucher(@Valid @RequestBody VoucherRequest request) {
        return new ApiResponse<>(201, "Tạo mã giảm giá thành công", voucherService.createVoucher(request));
    }

    @GetMapping
    public ApiResponse<List<VoucherResponse>> getAllVouchers() {
        return new ApiResponse<>(200, "Thành công", voucherService.getAllVouchers());
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
