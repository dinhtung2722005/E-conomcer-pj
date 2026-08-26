package com.example.demo.user.dto_request;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationRequest {
    @Size(min = 3, max = 20, message = "USERNAME_INVALID")
    private String Username;
    
    private String Email;

    @Size(min = 6, max = 20, message = "PASSWORD_INVALID")
    private String Password;
    
}
