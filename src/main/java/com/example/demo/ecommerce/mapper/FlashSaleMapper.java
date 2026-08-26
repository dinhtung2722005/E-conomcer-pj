package com.example.demo.ecommerce.mapper;

import com.example.demo.ecommerce.dto.FlashSaleResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FlashSaleMapper {
    FlashSaleResponse toFlashSaleResponse(FlashSale flashSale);
}