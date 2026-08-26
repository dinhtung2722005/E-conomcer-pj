package com.example.demo.user.service;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;
    @Value("${cloudinary.folder.avatar}")
    private String folder;
    public String uploadImage(MultipartFile file) throws IOException {
       Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(), 
                ObjectUtils.asMap(
                        "folder", folder,
                        "transformation", "c_fill,w_500,h_500" 
                )
        );        
      
        return uploadResult.get("secure_url").toString();
    }
}