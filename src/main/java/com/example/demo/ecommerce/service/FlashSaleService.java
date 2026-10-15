package com.example.demo.ecommerce.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.FlashSaleRequest;
import com.example.demo.ecommerce.dto.FlashSaleResponse;
import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleStatus;
import com.example.demo.ecommerce.mapper.FlashSaleMapper;
import com.example.demo.ecommerce.repository.FlashSaleRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlashSaleService {

    private final FlashSaleRepository flashSaleRepository;
    private final FlashSaleMapper flashSaleMapper;

    @Transactional
    public FlashSaleResponse createFlashSale(FlashSaleRequest request) {
        validateTimeRange(request);

        FlashSale flashSale = FlashSale.builder()
                .name(request.getName().trim())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(resolveStatus(request.getStartTime(), request.getEndTime()))
                .build();

        return flashSaleMapper.toFlashSaleResponse(flashSaleRepository.save(flashSale));
    }

    @Transactional(readOnly = true)
    public PageResponse<FlashSaleResponse> getAllFlashSales(PageQuery pagination) {
        return PageResponse.from(flashSaleRepository.findAll(pagination.toPageable())
                .map(flashSaleMapper::toFlashSaleResponse));
    }

    @Transactional(readOnly = true)
    public FlashSaleResponse getFlashSaleById(Long id) {
        return flashSaleMapper.toFlashSaleResponse(findFlashSale(id));
    }

    /**
     * Các chương trình đang chạy.
     *
     * <p>Bản cũ gọi {@code findByStatus("ACTIVE")} — giá trị không tồn tại trong
     * {@link FlashSaleStatus} nên luôn trả về rỗng.
     */
    @Transactional(readOnly = true)
    public PageResponse<FlashSaleResponse> getActiveFlashSales(PageQuery pagination) {
        return PageResponse.from(flashSaleRepository.findByStatus(FlashSaleStatus.ONGOING, pagination.toPageable())
                .map(flashSaleMapper::toFlashSaleResponse));
    }

    @Transactional
    public FlashSaleResponse updateFlashSale(Long id, FlashSaleRequest request) {
        FlashSale flashSale = findFlashSale(id);
        validateTimeRange(request);

        flashSale.setName(request.getName().trim());
        flashSale.setStartTime(request.getStartTime());
        flashSale.setEndTime(request.getEndTime());
        flashSale.setStatus(resolveStatus(request.getStartTime(), request.getEndTime()));

        return flashSaleMapper.toFlashSaleResponse(flashSaleRepository.save(flashSale));
    }

    @Transactional
    public void deleteFlashSale(Long id) {
        flashSaleRepository.delete(findFlashSale(id));
    }

    private FlashSale findFlashSale(Long id) {
        return flashSaleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_NOT_FOUND));
    }

    private void validateTimeRange(FlashSaleRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(ErrorCode.FLASH_SALE_TIME_INVALID);
        }
    }

    /**
     * Suy ra trạng thái từ khung thời gian thay vì tin vào giá trị client gửi lên.
     * Nhờ vậy không còn tình trạng chương trình đang trong khung giờ nhưng vẫn
     * mang trạng thái UPCOMING và không bao giờ được áp dụng.
     */
    private FlashSaleStatus resolveStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime)) {
            return FlashSaleStatus.UPCOMING;
        }
        if (now.isAfter(endTime)) {
            return FlashSaleStatus.ENDED;
        }
        return FlashSaleStatus.ONGOING;
    }
}
