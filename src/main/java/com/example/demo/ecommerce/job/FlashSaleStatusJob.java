package com.example.demo.ecommerce.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.entity.FlashSale;
import com.example.demo.ecommerce.entity.FlashSaleStatus;
import com.example.demo.ecommerce.repository.FlashSaleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tự động chuyển trạng thái flash sale theo thời gian.
 *
 * <p>Trước đây trạng thái phải được sửa tay. Nếu quên, một chương trình đang trong
 * khung giờ nhưng vẫn mang trạng thái {@code UPCOMING} sẽ không bao giờ được áp dụng,
 * vì cả truy vấn giá lẫn truy vấn hiển thị đều lọc theo {@code ONGOING}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleStatusJob {

    private final FlashSaleRepository flashSaleRepository;

    @Scheduled(fixedDelayString = "${app.flash-sale.status-sync-interval-ms:60000}")
    @Transactional
    public void syncStatuses() {
        LocalDateTime now = LocalDateTime.now();
        List<FlashSale> sales = flashSaleRepository.findNeedingStatusSync(
                now, FlashSaleStatus.ONGOING, FlashSaleStatus.ENDED);

        if (sales.isEmpty()) {
            return;
        }

        for (FlashSale sale : sales) {
            FlashSaleStatus expected = expectedStatus(sale, now);
            if (sale.getStatus() != expected) {
                sale.setStatus(expected);
                log.info("Flash sale '{}' (id={}) chuyển trạng thái sang {}", sale.getName(), sale.getId(), expected);
            }
        }

        flashSaleRepository.saveAll(sales);
    }

    private FlashSaleStatus expectedStatus(FlashSale sale, LocalDateTime now) {
        if (now.isBefore(sale.getStartTime())) {
            return FlashSaleStatus.UPCOMING;
        }
        if (now.isAfter(sale.getEndTime())) {
            return FlashSaleStatus.ENDED;
        }
        return FlashSaleStatus.ONGOING;
    }
}
