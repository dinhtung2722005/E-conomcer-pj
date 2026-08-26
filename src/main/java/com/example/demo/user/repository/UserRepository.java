package com.example.demo.user.repository;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.User;
@Repository
public interface UserRepository extends JpaRepository<User, Long>,JpaSpecificationExecutor {
Optional<User> findByUsername(String username);
    @Query("SELECT u FROM User u WHERE " +
       "(:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
       "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))") 
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

}