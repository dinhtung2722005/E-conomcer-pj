package com.example.demo.ecommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.VoucherRequest;
import com.example.demo.ecommerce.dto.VoucherResponse;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.mapper.VoucherMapper;
import com.example.demo.ecommerce.repository.VoucherRepository;

import jakarta.transaction.Transactional;
@Service
public class VoucherService {
    private final VoucherRepository voucherRepository;
    private final VoucherMapper voucherMapper;
    public VoucherService(VoucherRepository voucherRepository,VoucherMapper voucherMapper){
        this.voucherMapper = voucherMapper;
        this.voucherRepository = voucherRepository;
    }
    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
    if (voucherRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Mã giảm giá đã tồn tại: " + request.getCode());
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("Ngày kết thúc phải sau ngày bắt đầu!");
        }  
    Voucher voucher = Voucher.builder()
                .code(request.getCode().toUpperCase())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderValue(request.getMinOrderValue())
                .totalQuantity(request.getTotalQuantity())
                .usedQuantity(0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Voucher savedVoucher = voucherRepository.save(voucher);
        return voucherMapper.toVoucherResponse(savedVoucher);

    }
    public List<VoucherResponse> getAllVouchers() {
        return voucherRepository.findAll()
                .stream()
                .map(voucherMapper::toVoucherResponse)
                .toList();
    }
    public VoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục có ID: " + id));
        return voucherMapper.toVoucherResponse(voucher);
    }
    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Voucher!"));

        if (!voucher.getCode().equalsIgnoreCase(request.getCode()) && voucherRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Mã code mới bị trùng lặp!");
        }

        voucher.setCode(request.getCode().toUpperCase());
        voucher.setDiscountType(request.getDiscountType());
        voucher.setDiscountValue(request.getDiscountValue());
        voucher.setMaxDiscountAmount(request.getMaxDiscountAmount());
        voucher.setMinOrderValue(request.getMinOrderValue());
        
    
        if (request.getTotalQuantity() < voucher.getUsedQuantity()) {
            throw new RuntimeException("Tổng số lượng không được nhỏ hơn số lượng mã đã sử dụng (" + voucher.getUsedQuantity() + ")");
        }
        voucher.setTotalQuantity(request.getTotalQuantity());
        
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());

        return voucherMapper.toVoucherResponse(voucherRepository.save(voucher));
    }
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Voucher!"));
        
        voucherRepository.delete(voucher);
    }
}
