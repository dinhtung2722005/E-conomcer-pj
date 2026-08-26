package com.example.demo.ecommerce.service;

import com.example.demo.ecommerce.dto.FlashSaleRequest;
import com.example.demo.ecommerce.dto.FlashSaleResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.mapper.FlashSaleMapper;
import com.example.demo.ecommerce.repository.FlashSaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FlashSaleService {

    @Autowired
    private FlashSaleRepository flashSaleRepository;

    @Autowired
    private FlashSaleMapper flashSaleMapper;

    // 1. TẠO MỚI CHƯƠNG TRÌNH FLASH SALE
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

    // 2. LẤY DANH SÁCH
    public List<FlashSaleResponse> getAllFlashSales() {
        return flashSaleRepository.findAll()
                .stream()
                .map(flashSaleMapper::toFlashSaleResponse)
                .toList();
    }

    // 3. LẤY CHI TIẾT 1 CHƯƠNG TRÌNH
    public FlashSaleResponse getFlashSaleById(Long id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Flash Sale!"));
        return flashSaleMapper.toFlashSaleResponse(flashSale);
    }

    // 4. CẬP NHẬT CHƯƠNG TRÌNH
    @Transactional
    public FlashSaleResponse updateFlashSale(Long id, FlashSaleRequest request) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Flash Sale!"));

        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().isEqual(request.getStartTime())) {
            throw new RuntimeException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu!");
        }

        flashSale.setName(request.getName());
        flashSale.setStartTime(request.getStartTime());
        flashSale.setEndTime(request.getEndTime());
        flashSale.setStatus(request.getStatus());

        return flashSaleMapper.toFlashSaleResponse(flashSaleRepository.save(flashSale));
    }

    // 5. XÓA CHƯƠNG TRÌNH (Sẽ tự động xóa luôn các FlashSaleItem bên trong nhờ orphanRemoval = true)
    @Transactional
    public void deleteFlashSale(Long id) {
        FlashSale flashSale = flashSaleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Flash Sale!"));
        
        flashSaleRepository.delete(flashSale);
    }
}