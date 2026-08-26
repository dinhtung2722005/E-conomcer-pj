package com.example.demo.user.dto_request;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileUpdateRequest {
    // Chỉ khai báo những trường cho phép người dùng sửa
    private String Email; 
    
    // private String fullName;
    // private String phone;
}