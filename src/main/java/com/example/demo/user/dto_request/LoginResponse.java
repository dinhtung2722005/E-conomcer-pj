package com.example.demo.user.dto_request;
import com.example.demo.user.entity.User;
public class LoginResponse {
    private User user;
    private String token;

    public LoginResponse(User user, String token) {
        this.user = user;
        this.token = token;
    }

    // Getters
    public User getUser() { return user; }
    public String getToken() { return token; }

    // Setters
    public void setUser(User user) { this.user = user; }
    public void setToken(String token) { this.token = token; }
}