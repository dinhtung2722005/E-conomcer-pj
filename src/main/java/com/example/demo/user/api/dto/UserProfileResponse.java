package com.example.demo.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isActive")
    private boolean active;
}
