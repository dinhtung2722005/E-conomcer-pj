package com.example.demo.user.entity;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invalidated_tokens")
public class InvalidatedToken {
    
    @Id
    private String id; 
    
    private Date expiryTime; 

    public InvalidatedToken() {}

    public InvalidatedToken(String id, Date expiryTime) {
        this.id = id;
        this.expiryTime = expiryTime;
    }

    // Getters và Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Date getExpiryTime() { return expiryTime; }
    public void setExpiryTime(Date expiryTime) { this.expiryTime = expiryTime; }
}