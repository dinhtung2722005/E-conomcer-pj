package com.example.demo.user.service;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    @Value("${cloudinary.folder.avatar}")
    private String folder;

    /**
     * Upload ảnh lên Cloudinary, trả về URL công khai.
     *
     * <p>Nhận {@code byte[]} thay vì {@code MultipartFile} để tầng controller có thể
     * kiểm tra nội dung file trước khi gửi đi (xem {@code ProfileController}).
     */
    public String uploadImage(byte[] content) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(
                content,
                ObjectUtils.asMap(
                        "folder", folder,
                        "transformation", "c_fill,w_500,h_500"));

        Object secureUrl = uploadResult.get("secure_url");
        if (secureUrl == null) {
            throw new IOException("Cloudinary không trả về URL ảnh");
        }
        return secureUrl.toString();
    }
}
