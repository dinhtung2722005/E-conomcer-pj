package com.example.demo.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor 
@NoArgsConstructor 
public class OrderRequest {
       private String receiverName;
       private String receiverPhone;
        private String shippingAddress;
        private String voucherCode;
}
