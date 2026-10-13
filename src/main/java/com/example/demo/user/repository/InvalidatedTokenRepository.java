package com.example.demo.user.repository;

import java.util.Date;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.InvalidatedToken;

@Repository
public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, String> {

    /** Xóa các token đã hết hạn khỏi blacklist (chúng không còn giá trị chặn). */
    long deleteByExpiryTimeBefore(Date time);
}
