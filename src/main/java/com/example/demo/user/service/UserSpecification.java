package com.example.demo.user.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.example.demo.user.entity.User;

import jakarta.persistence.criteria.Predicate;

public class UserSpecification {

    public static Specification<User> filterUsers(String keyword, Boolean isActive, String role) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Lọc theo từ khóa (Tìm trong username HOẶC email)
            if (keyword != null && !keyword.isEmpty()) {
                String likeKeyword = "%" + keyword.toLowerCase() + "%";
                Predicate usernameMatch = cb.like(cb.lower(root.get("username")), likeKeyword);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), likeKeyword);
                predicates.add(cb.or(usernameMatch, emailMatch)); // Tương đương: (username LIKE ? OR email LIKE ?)
            }

            // 2. Lọc theo trạng thái (Khóa hoặc Mở)
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            // 3. Lọc theo quyền (Role)
            if (role != null && !role.isEmpty()) {
                predicates.add(cb.equal(root.get("role"), role)); 
                // Lưu ý: Nếu Role của bạn là Enum, có thể cần parse role sang Enum trước khi compare
            }

            // Ghép tất cả các điều kiện lại bằng toán tử AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}