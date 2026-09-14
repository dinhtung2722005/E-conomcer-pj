package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.FlashSaleRequest;
import com.example.demo.ecommerce.dto.FlashSaleResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.mapper.FlashSaleMapper;
import com.example.demo.ecommerce.repository.FlashSaleRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

@Service
public class FlashSaleService {

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private FlashSaleMapper flashSaleMapper;

    @Transactional
    public FlashSaleResponse createFlashSale(FlashSaleRequest request) {
        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().isEqual(request.getStartTime())) {
            throw new RuntimeException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu!");
        }

        FlashSale flashSale = FlashSale.builder()
                .name(request.getName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(request.getStatus())
                .build();

        return flashSaleMapper.toFlashSaleResponse(flashSaleRepository.save(flashSale));
    }

    public List<FlashSaleResponse> getAllFlashSales() {
        return flashSaleRepository.findAll()
                .stream()
                .map(flashSaleMapper::toFlashSaleResponse)
                .toList();
    }

    public FlashSaleResponse getFlashSaleById(Long id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Flash_Sale_Not_Found));
        return flashSaleMapper.toFlashSaleResponse(flashSale);
    }
    public List<FlashSaleResponse> getActiveFlashSales() {
        return flashSaleRepository.findByStatus("ACTIVE")
                .stream()
                .map(flashSaleMapper::toFlashSaleResponse)
                .toList();
    }
    
    @Transactional
    public FlashSaleResponse updateFlashSale(Long id, FlashSaleRequest request) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Flash_Sale_Not_Found));

        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().isEqual(request.getStartTime())) {
            throw new RuntimeException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu!");
        }

        flashSale.setName(request.getName());
        flashSale.setStartTime(request.getStartTime());
        flashSale.setEndTime(request.getEndTime());
        flashSale.setStatus(request.getStatus());

        return flashSaleMapper.toFlashSaleResponse(flashSaleRepository.save(flashSale));
    }

    @Transactional
    public void deleteFlashSale(Long id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.Flash_Sale_Not_Found));
        
        flashSaleRepository.delete(flashSale);
    }
}