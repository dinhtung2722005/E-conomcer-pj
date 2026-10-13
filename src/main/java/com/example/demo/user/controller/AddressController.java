package com.example.demo.user.controller;

import java.util.List;

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
import com.example.demo.user.service.CurrentUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> getMyAddresses() {
        return new ApiResponse<>(200, "Thành công",
                addressService.getUserAddresses(currentUserService.getCurrentUsername()));
    }

    @PostMapping
    public ApiResponse<AddressResponse> addAddress(@Valid @RequestBody AddressRequest request) {
        return new ApiResponse<>(200, "Thêm địa chỉ thành công",
                addressService.addAddress(currentUserService.getCurrentUsername(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> updateAddress(@PathVariable Long id,
                                                      @Valid @RequestBody AddressRequest request) {
        return new ApiResponse<>(200, "Cập nhật địa chỉ thành công",
                addressService.updateAddress(currentUserService.getCurrentUsername(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(currentUserService.getCurrentUsername(), id);
        return new ApiResponse<>(200, "Xóa địa chỉ thành công", null);
    }
}
