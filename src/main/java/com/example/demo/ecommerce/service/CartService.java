package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.demo.ecommerce.dto.CartCustomerResponse;
import com.example.demo.ecommerce.dto.CartItemCustomerResponse;
import com.example.demo.ecommerce.entity.Cart;
import com.example.demo.ecommerce.entity.CartItem;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.ProductStatus;
import com.example.demo.ecommerce.repository.CartRepository;
import com.example.demo.ecommerce.repository.ProductRepository;

import jakarta.transaction.Transactional;

@Service 
public class CartService {
    private final CartRepository cartRepository;
    private final FlashSaleItemService flashSaleItemService;
    private final ProductRepository productRepository;
    public CartService(CartRepository cartRepository, FlashSaleItemService flashSaleItemService, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.flashSaleItemService = flashSaleItemService    ;
        this.productRepository = productRepository;
    } 
   @Transactional
    public void addToCart(Long userId, Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new RuntimeException("Sản phẩm đã ngừng kinh doanh!");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .build();
            cart.addItem(newItem); 
        }

        cartRepository.save(cart);
    }
    public CartCustomerResponse getCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));

        BigDecimal totalCartPrice = BigDecimal.ZERO;

        List<CartItemCustomerResponse> itemResponses = cart.getItems().stream()
                .map(item -> {
                    Product product = item.getProduct();
                    BigDecimal flashSalePrice = flashSaleItemService.getActiveFlashSalePrice(product.getId());
                    BigDecimal currentPrice = (flashSalePrice != null) ? flashSalePrice : product.getPrice();
                    
                    return new CartItemCustomerResponse(
        item.getId(),          
        product.getId(),        
        product.getName(),      
        item.getQuantity(),   
        product.getPrice(),    
        currentPrice     
);
                })
                .toList();

        for (CartItemCustomerResponse res : itemResponses) {
            BigDecimal itemTotal = res.getCurrentPrice().multiply(BigDecimal.valueOf(res.getQuantity()));
            totalCartPrice = totalCartPrice.add(itemTotal);
        }

        return new CartCustomerResponse(cart.getId(), itemResponses, totalCartPrice);
    }
    @Transactional
    public void updateCartItemQuantity(Long userId, Long cartItemId, Integer newQuantity) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giỏ hàng!"));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại trong giỏ hàng!"));

        if (newQuantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(newQuantity);
        }
        
        cartRepository.save(cart);
    }
    @Transactional
    public void removeCartItem(Long userId, Long cartItemId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giỏ hàng!"));

        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(cartItemId));
        
        if (!removed) {
            throw new RuntimeException("Sản phẩm không tồn tại trong giỏ hàng!");
        }

        cartRepository.save(cart);
    }
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giỏ hàng!"));
        
        cart.getItems().clear(); 
        cartRepository.save(cart);
    }
}
