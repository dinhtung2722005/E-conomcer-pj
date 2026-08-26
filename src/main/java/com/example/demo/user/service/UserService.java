package com.example.demo.user.service;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.dto_request.ChangePasswordRequest;
import com.example.demo.user.dto_request.UserCreationRequest;
import com.example.demo.user.dto_request.UserProfileResponse;
import com.example.demo.user.dto_request.UserProfileUpdateRequest;
import com.example.demo.user.dto_request.UserUpdateRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.UserRepository;
@Service
public class UserService {
    private final UserRepository userRespository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRespository,PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.userRespository = userRespository;
    }
    public User createUser(UserCreationRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setRole(UserRole.CUSTOMER);
        return userRespository.save(user);
    }
    public List<User> getAllUsers() {
        return userRespository.findAll();
    }
    public User getUserById(Long id) {
        return userRespository.findById(id).orElseThrow(() -> new RuntimeException("Khong tim thay user co id:  " + id));
    }
    public User updateUser(Long id, UserUpdateRequest request) {
        User user=getUserById(id);
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        return userRespository.save(user);
    }
    public void deleteUser(Long id) {
        User user=getUserById(id);
        userRespository.delete(user);
    }
    public User getUserByUsername(String username) {
        return userRespository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
    public UserProfileResponse profile(String username) {
    User user = userRespository.findByUsername(username)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            UserProfileResponse response = new UserProfileResponse();
            response.setId(user.getId());
            response.setUsername(user.getUsername());
            response.setEmail(user.getEmail());
            response.setRole(user.getRole().name());
            return response;
    
}
   public UserProfileResponse updateProfile(String username, UserProfileUpdateRequest request) {
    User user = userRespository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            user.setEmail(request.getEmail());
        }
        
        
        userRespository.save(user);

        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());
        return response;
    }
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        // 1. Tìm user
        User user = userRespository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USERNOTFOUND));

        // 2. Kiểm tra mật khẩu cũ xem có khớp với database không
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Mật khẩu cũ không chính xác!");
        }

        // 3. Kiểm tra mật khẩu mới và mật khẩu xác nhận
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp!");
        }
        
        if (request.getNewPassword().equals(request.getOldPassword())) {
            throw new RuntimeException("Mật khẩu mới không được giống mật khẩu cũ!");
        }

        // 4. Mã hóa mật khẩu mới và lưu lại
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRespository.save(user);
    }
    @Transactional
    public UserProfileResponse updateAvatar(String username, String avatarUrl) {
        // 1. Tìm user trong DB
        User user = userRespository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        user.setAvatar(avatarUrl);
        userRespository.save(user);

        UserProfileResponse response =new UserProfileResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setAvatar(user.getAvatar());
       return response;
    }
  

}
