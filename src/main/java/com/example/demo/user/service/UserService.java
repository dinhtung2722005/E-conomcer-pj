package com.example.demo.user.service;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final com.example.demo.wallet.service.WalletService walletService;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevocationService sessionRevocationService;

    /** Tạo người dùng với vai trò CUSTOMER. Mật khẩu luôn được hash. */
    @Transactional
    public UserProfileResponse createUser(UserCreationRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        walletService.initializeWallet(saved.getId());
        return toProfileResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(Long id) {
        return toProfileResponse(findUserById(id));
    }

    /**
     * Cập nhật email. Không cho phép thay đổi mật khẩu ở đây — mật khẩu chỉ do
     * chính chủ tài khoản đổi qua {@link #changePassword}.
     */
    @Transactional
    public UserProfileResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findUserById(id);
        String email = request.getEmail().trim().toLowerCase();

        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        user.setEmail(email);
        return toProfileResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw new AppException(ErrorCode.CANNOT_DELETE_SELF);
        }
        User user = findUserById(id);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String username) {
        return toProfileResponse(findUserByUsername(username));
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UserProfileUpdateRequest request) {
        User user = findUserByUsername(username);
        String email = request.getEmail().trim().toLowerCase();

        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        user.setEmail(email);
        return toProfileResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_UNCHANGED);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        sessionRevocationService.revokeAllSessions(user.getId());
    }

    @Transactional
    public UserProfileResponse updateAvatar(String username, String avatarUrl) {
        User user = findUserByUsername(username);
        user.setAvatar(avatarUrl);
        return toProfileResponse(userRepository.save(user));
    }

    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private static UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .avatar(user.getAvatar())
                .active(user.isActive())
                .build();
    }
}
