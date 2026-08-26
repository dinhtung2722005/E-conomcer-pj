package com.example.demo.ecommerce.mapper;

import org.mapstruct.Mapper;

import com.example.demo.ecommerce.dto.VoucherResponse;
import com.example.demo.ecommerce.entity.Voucher;


@Mapper(componentModel = "spring")
public interface VoucherMapper {
    
    VoucherResponse toVoucherResponse(Voucher voucher);
}