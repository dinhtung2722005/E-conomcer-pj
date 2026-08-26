package com.example.demo.user.controller;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.UserCreationRequest;
import com.example.demo.user.dto_request.UserUpdateRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.service.UserService;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @PostMapping
    ApiResponse<User> createUser(@RequestBody UserCreationRequest request) {
        ApiResponse<User> response = new ApiResponse<>();
        response.setCode(201);
        response.setResult(userService.createUser(request));
        return response;
        
    }
    @GetMapping
    ApiResponse<List<User>> getAllUsers() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("Authenticated user: {}", authentication.getName());
        authentication.getAuthorities().forEach(authority -> log.info("Authority: {}", authority.getAuthority()));
        ApiResponse<List<User>> response = new ApiResponse<>();
        response.setCode(200);
        response.setResult(userService.getAllUsers());
        return response;
    }
    @GetMapping("/{id}")                                                                                                    
    User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }
    @PutMapping("/{id}")
    User updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return userService.updateUser(id, request);
    }
    @DeleteMapping("/{id}")
    void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
