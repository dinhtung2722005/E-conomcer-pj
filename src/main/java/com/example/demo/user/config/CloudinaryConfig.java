package com.example.demo.user.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.cloudinary.Cloudinary;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        if (!StringUtils.hasText(cloudName) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            // Không chặn khởi động: các tính năng khác vẫn chạy, chỉ upload ảnh là lỗi.
            log.warn("Cloudinary chưa được cấu hình (CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / "
                    + "CLOUDINARY_API_SECRET). Chức năng upload ảnh đại diện sẽ không hoạt động.");
        }

        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        return new Cloudinary(config);
    }
}
