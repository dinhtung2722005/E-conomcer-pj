package com.example.demo.user.api.dto;

import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class AddressResponse {
    private Long id;
    private String recipientName;
    private String phoneNumber;
    private String fullAddress; 
    private boolean isDefault;
}