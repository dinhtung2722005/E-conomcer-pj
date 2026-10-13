package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.OrderRequest;
import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Cart;
import com.example.demo.ecommerce.entity.CartItem;
import com.example.demo.ecommerce.entity.DiscountType;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.ProductStatus;
import com.example.demo.ecommerce.entity.UserVoucher;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.mapper.OrderMapper;
import com.example.demo.ecommerce.repository.CartRepository;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.repository.UserVoucherRepository;
import com.example.demo.ecommerce.repository.VoucherRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Đặt hàng.
 *
 * <p>Toàn bộ thao tác nằm trong một transaction: hoặc đơn được tạo cùng lúc với
 * việc giữ chỗ kho và trừ lượt voucher, hoặc không có gì thay đổi.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    /** Mọi số tiền được làm tròn về 2 chữ số thập phân (đơn vị tiền tệ). */
    private static final int MONEY_SCALE = 2;

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final FlashSaleItemService flashSaleItemService;
    private final InventoryService inventoryService;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest request) {
        // Khóa giỏ hàng: hai request checkout đồng thời của cùng người dùng sẽ chạy tuần tự.
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        if (cart.getItems().isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        Order order = Order.builder()
                .userId(userId)
                .receiverName(request.getReceiverName())
                .receiverPhone(request.getReceiverPhone())
                .shippingAddress(request.getShippingAddress())
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE,
                        "Sản phẩm '" + product.getName() + "' đã ngừng kinh doanh");
            }

            // Thứ tự khóa luôn cố định (flash sale item trước, tồn kho sau) để tránh deadlock.
            BigDecimal unitPrice = flashSaleItemService.reserveFlashSaleItem(product, cartItem.getQuantity());
            inventoryService.deductInventory(product.getId(), cartItem.getQuantity());

            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));

            order.addItem(OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .priceAtPurchase(unitPrice)
                    .build());
        }

        totalAmount = totalAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        order.setTotalAmount(totalAmount);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            Voucher voucher = lockVoucher(request.getVoucherCode());
            discountAmount = applyVoucher(voucher, userId, totalAmount);
            order.setVoucher(voucher);
        }

        order.setDiscountAmount(discountAmount);
        order.setFinalAmount(totalAmount.subtract(discountAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP));

        Order savedOrder = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        return orderMapper.toResponse(savedOrder);
    }

    /** Khóa dòng voucher để hai đơn đồng thời không cùng vượt hạn mức sử dụng. */
    private Voucher lockVoucher(String rawCode) {
        String code = rawCode.trim().toUpperCase();
        return voucherRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    /**
     * Kiểm tra điều kiện áp dụng, tính số tiền giảm và ghi nhận lượt sử dụng.
     *
     * <p>Voucher phải nằm trong ví của chính người dùng đang đặt hàng — trước đây
     * bất kỳ ai biết mã cũng dùng được, không cần lưu vào ví.
     */
    private BigDecimal applyVoucher(Voucher voucher, Long userId, BigDecimal totalAmount) {
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
            throw new AppException(ErrorCode.VOUCHER_USAGE_LIMIT);
        }
        if (totalAmount.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new AppException(ErrorCode.VOUCHER_MIN_ORDER);
        }

        UserVoucher userVoucher = userVoucherRepository.findByUserIdAndVoucherId(userId, voucher.getId())
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_SAVED));

        if (Boolean.TRUE.equals(userVoucher.getIsUsed())) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_USED);
        }

        BigDecimal discountAmount = calculateDiscount(voucher, totalAmount);

        voucher.setUsedQuantity(voucher.getUsedQuantity() + 1);
        voucherRepository.save(voucher);

        userVoucher.setIsUsed(true);
        userVoucherRepository.save(userVoucher);

        return discountAmount;
    }

    /**
     * Tính tiền giảm theo đúng loại voucher.
     *
     * <p>Bản cũ luôn nhân với {@code discountValue / 100} nên voucher
     * {@link DiscountType#FIXED_AMOUNT} bị tính sai, đồng thời ném NPE khi
     * {@code maxDiscountAmount} là null.
     */
    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal totalAmount) {
        BigDecimal discount;

        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = totalAmount
                    .multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);

            if (voucher.getMaxDiscountAmount() != null) {
                discount = discount.min(voucher.getMaxDiscountAmount());
            }
        } else {
            discount = voucher.getDiscountValue();
        }

        // Không bao giờ giảm quá giá trị đơn hàng (tránh finalAmount âm).
        if (discount.compareTo(totalAmount) > 0) {
            discount = totalAmount;
        }
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }

        return discount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
