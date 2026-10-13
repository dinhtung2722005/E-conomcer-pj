package com.example.demo.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.ecommerce.dto.CartCustomerResponse;
import com.example.demo.ecommerce.dto.CartItemCustomerResponse;
import com.example.demo.ecommerce.entity.Cart;
import com.example.demo.ecommerce.entity.CartItem;
import com.example.demo.ecommerce.entity.Inventory;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.entity.ProductStatus;
import com.example.demo.ecommerce.repository.CartRepository;
import com.example.demo.ecommerce.repository.InventoryRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Giỏ hàng của khách.
 *
 * <p>Mọi thao tác ghi đều khóa dòng giỏ hàng theo người dùng, nên không thể xảy ra
 * tình trạng hai request đồng thời cùng thêm một sản phẩm và sinh ra hai dòng trùng.
 *
 * <p>Lưu ý: giỏ hàng <b>không</b> giữ chỗ tồn kho. Việc trừ kho thật diễn ra ở bước
 * đặt hàng ({@link CheckoutService}).
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MONEY_SCALE = 2;

    private final CartRepository cartRepository;
    private final FlashSaleItemService flashSaleItemService;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public void addToCart(Long userId, Long productId, Integer quantity) {
        checkQuantity(quantity);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new AppException(ErrorCode.PRODUCT_NOT_ACTIVE);
        }

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).build()));

        Optional<CartItem> existingItem = findItem(cart, productId);
        int expectedQuantity = existingItem.map(item -> item.getQuantity() + quantity).orElse(quantity);

        if (inventory.getAvailableQuantity() < expectedQuantity) {
            throw new AppException(ErrorCode.OUT_OF_STOCK,
                    "Kho chỉ còn " + inventory.getAvailableQuantity() + " sản phẩm");
        }

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(expectedQuantity);
        } else {
            cart.addItem(CartItem.builder().product(product).quantity(quantity).build());
        }

        cartRepository.save(cart);
    }

    /**
     * Xem giỏ hàng.
     *
     * <p>Chỉ đọc — trước đây method này còn tạo mới giỏ rỗng và ghi vào DB ngay trong
     * một request GET. Khách chưa có giỏ sẽ nhận về giỏ rỗng với {@code cartId = null}.
     */
    @Transactional(readOnly = true)
    public CartCustomerResponse getCart(Long userId) {
        Optional<Cart> cartOptional = cartRepository.findByUserId(userId);
        if (cartOptional.isEmpty()) {
            return new CartCustomerResponse(null, List.of(), BigDecimal.ZERO);
        }

        Cart cart = cartOptional.get();
        List<CartItem> items = cart.getItems();

        // Một truy vấn duy nhất cho tất cả sản phẩm, thay vì một truy vấn cho mỗi dòng.
        Map<Long, BigDecimal> flashSalePrices = flashSaleItemService.getActiveFlashSalePrices(
                items.stream().map(item -> item.getProduct().getId()).toList());

        List<CartItemCustomerResponse> itemResponses = new ArrayList<>();
        BigDecimal totalCartPrice = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = item.getProduct();
            BigDecimal currentPrice = flashSalePrices.getOrDefault(product.getId(), product.getPrice());

            itemResponses.add(new CartItemCustomerResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    item.getQuantity(),
                    product.getPrice(),
                    currentPrice));

            totalCartPrice = totalCartPrice.add(
                    currentPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        return new CartCustomerResponse(cart.getId(), itemResponses,
                totalCartPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    @Transactional
    public void updateCartItemQuantity(Long userId, Long cartItemId, Integer newQuantity) {
        if (newQuantity == null) {
            throw new AppException(ErrorCode.INVALID_QUANTITY);
        }

        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        CartItem item = findItemById(cart, cartItemId);

        if (newQuantity <= 0) {
            cart.getItems().remove(item);
        } else {
            Inventory inventory = inventoryRepository.findByProductId(item.getProduct().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_NOT_FOUND));

            if (inventory.getAvailableQuantity() < newQuantity) {
                throw new AppException(ErrorCode.OUT_OF_STOCK,
                        "Kho chỉ còn " + inventory.getAvailableQuantity() + " sản phẩm");
            }
            item.setQuantity(newQuantity);
        }

        cartRepository.save(cart);
    }

    @Transactional
    public void removeCartItem(Long userId, Long cartItemId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        CartItem item = findItemById(cart, cartItemId);
        cart.getItems().remove(item);
        cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CART_NOT_FOUND));

        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Optional<CartItem> findItem(Cart cart, Long productId) {
        return cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();
    }

    /** Tìm dòng hàng <b>trong giỏ của chính người dùng</b> — không thể thao tác dòng của giỏ khác. */
    private CartItem findItemById(Cart cart, Long cartItemId) {
        return cart.getItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    private void checkQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new AppException(ErrorCode.INVALID_QUANTITY);
        }
    }
}
