package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.OrderRequest;
import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.Cart;
import com.example.demo.ecommerce.entity.CartItem;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.Voucher;
import com.example.demo.ecommerce.mapper.OrderMapper;
import com.example.demo.ecommerce.repository.CartRepository;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.repository.VoucherRepository;

@Service
public class CheckoutService {

    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private VoucherRepository voucherRepository;
    
    @Autowired
    private FlashSaleItemService flashSaleItemService;
    
    @Autowired
    private InventoryService inventoryService; 
    
    @Autowired
    private OrderMapper orderMapper;

    @Transactional
    public OrderResponse placeOrder(Long userId, OrderRequest request) {
       Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Giỏ hàng không tồn tại!"));

        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Giỏ hàng đang trống, không thể đặt hàng!");
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
            
          
            inventoryService.deductInventory(product.getId(), cartItem.getQuantity());

            
            BigDecimal flashSalePrice = flashSaleItemService.getActiveFlashSalePrice(product.getId());
            BigDecimal currentPrice = (flashSalePrice != null) ? flashSalePrice : product.getPrice();

            BigDecimal itemTotal = currentPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);

          
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .priceAtPurchase(currentPrice)
                    .build();
            order.addItem(orderItem);
        }
        
        order.setTotalAmount(totalAmount);
        BigDecimal discountAmount = BigDecimal.ZERO;

        
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            Voucher voucher = voucherRepository.findByCode(request.getVoucherCode())
                    .orElseThrow(() -> new RuntimeException("Mã giảm giá không tồn tại!"));

          
            if (LocalDateTime.now().isAfter(voucher.getEndDate())) {
                throw new RuntimeException("Mã giảm giá đã hết hạn!");
            }
            if (voucher.getUsedQuantity() >= voucher.getTotalQuantity()) {
                throw new RuntimeException("Mã giảm giá đã hết lượt sử dụng!");
            }
            if (totalAmount.compareTo(voucher.getMinOrderValue()) < 0) {
                throw new RuntimeException("Đơn hàng chưa đạt giá trị tối thiểu để sử dụng mã này!");
            }

           
    discountAmount = totalAmount
        .multiply(voucher.getDiscountValue())
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (discountAmount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discountAmount = voucher.getMaxDiscountAmount();
            }

      
            voucher.setUsedQuantity(voucher.getUsedQuantity() + 1);
            voucherRepository.save(voucher);

            order.setVoucher(voucher);
            order.setDiscountAmount(discountAmount);
        }

      
        order.setFinalAmount(totalAmount.subtract(discountAmount));
        Order savedOrder = orderRepository.save(order);

       
        cart.getItems().clear();
        cartRepository.save(cart);

        return orderMapper.toResponse(savedOrder);
    }
}