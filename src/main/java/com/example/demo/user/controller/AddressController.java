package com.example.demo.user.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.AddressRequest;
import com.example.demo.user.dto_request.AddressResponse;
import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.service.AddressService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressService addressService;

    // Lấy danh sách địa chỉ 
    @GetMapping
    public ApiResponse<List<AddressResponse>> getMyAddresses() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ApiResponse<>(200, "Thành công", addressService.getUserAddresses(username));
    }

    // Thêm địa chỉ mới
    @PostMapping
    public ApiResponse<AddressResponse> addAddress(@Valid @RequestBody AddressRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        AddressResponse response = addressService.addAddress(username, request);
        
        return new ApiResponse<>(200, "Thêm địa chỉ thành công", response);
    }

    // Xóa địa chỉ
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        addressService.deleteAddress(username, id);
        return new ApiResponse<>(200, "Xóa địa chỉ thành công", null);
    }
    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> updateAddress(@PathVariable Long id, @RequestBody AddressRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        AddressResponse response = addressService.updateAddress(username, id, request);
        return new ApiResponse<>(200, "Cập nhật địa chỉ thành công", response);
    }
}