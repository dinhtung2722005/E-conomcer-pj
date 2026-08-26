package com.example.demo.user.dto_request;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddressRequest {
    @NotBlank(message = "Tên người nhận không được để trống")
    private String recipientName;
    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;
    @NotBlank(message = "Tên đường/Số nhà không được để trống")
    private String street;
    private String ward;
    private String district;
    private String city;
    private boolean isDefault;
}