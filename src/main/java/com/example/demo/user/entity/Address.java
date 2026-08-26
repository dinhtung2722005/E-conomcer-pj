package com.example.demo.user.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Liên kết với User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Tên người nhận (Có thể khác tên thật của User)
    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    // Số điện thoại người nhận
    @Column(nullable = false, length = 20)
    private String phone; 

    // Chi tiết địa chỉ (Số nhà, ngõ, tên đường)
    @Column(nullable = false, length = 255)
    private String street;

    // Phường/Xã
    @Column(nullable = false, length = 100)
    private String ward;

    // Quận/Huyện
    @Column(nullable = false, length = 100)
    private String district;

    // Tỉnh/Thành phố
    @Column(nullable = false, length = 100)
    private String city;

    // Đánh dấu đây có phải là địa chỉ mặc định hay không
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false; // <-- SỬA Boolean (chữ hoa) THÀNH boolean (chữ thường)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}