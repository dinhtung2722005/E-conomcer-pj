package com.example.demo.user.dto_request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor   
@AllArgsConstructor  
public class UserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String avatar;
}