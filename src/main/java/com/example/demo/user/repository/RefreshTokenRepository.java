package com.example.demo.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.entity.User;
import jakarta.persistence.LockModeType;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Query("SELECT r.user.id FROM RefreshToken r WHERE r.token = :token")
    Optional<Long> findUserIdByToken(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    Optional<RefreshToken> findByToken(String token);
    int deleteByUser(User user); 
    int deleteByUser_Id(Long userId);
}
