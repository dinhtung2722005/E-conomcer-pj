package com.example.demo.ecommerce.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ecommerce.dto.CategoryRequest;
import com.example.demo.ecommerce.dto.VoucherRequest;
import com.example.demo.ecommerce.dto.VoucherResponse;
import com.example.demo.ecommerce.service.VoucherService;
import com.example.demo.user.dto_request.ApiResponse;

@RestController
@RequestMapping("/api/admin/voucher")
@PreAuthorize("hasRole('ADMIN')")
public class VoucherController {

    @Autowired
    private VoucherService voucherService;

    @PostMapping
    public ApiResponse<VoucherResponse> createVoucher(@RequestBody VoucherRequest request) {
        ApiResponse<VoucherResponse> response = new ApiResponse<>(200,null,voucherService.createVoucher(request));
        return response;
    }

    @GetMapping
    public ApiResponse<List<VoucherResponse>> getAllVouchers() {
       ApiResponse<List<VoucherResponse>> response = new ApiResponse<>(200,null,voucherService.getAllVouchers());
       return  response;
    }

    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> getVoucherById(@PathVariable Long id) {
        ApiResponse<VoucherResponse> response = new ApiResponse<>(200,null, voucherService.getVoucherById(id));
        return response;
    }

    @PutMapping("/{id}")
    public ApiResponse<VoucherResponse> updateVoucher(
            @PathVariable Long id, 
            @RequestBody CategoryRequest request) {
           ApiResponse<VoucherResponse>response = new ApiResponse<>(200,null, voucherService.getVoucherById(id));
           return response;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.ok("Xóa danh mục thành công!");
    }
}