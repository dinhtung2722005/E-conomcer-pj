package com.example.demo.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    
    List<Address> findByUser(User user);
    Optional<Address> findByIdAndUser(Long id, User user);
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.user = :user")
    void resetDefaultAddressForUser(User user);
}