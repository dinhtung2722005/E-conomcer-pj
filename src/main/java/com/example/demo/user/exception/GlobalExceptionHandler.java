package com.example.demo.user.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.demo.user.dto_request.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Chuyển mọi ngoại lệ thành {@link ApiResponse} với HTTP status đúng.
 *
 * <p>Không bao giờ trả về stack trace hay thông báo lỗi nội bộ của hệ thống cho client.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Lỗi nghiệp vụ đã lường trước. */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, HttpServletRequest request) {
        log.debug("Nghiệp vụ từ chối [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage());
    }

    /** Lỗi validate trên @RequestBody. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = ErrorCode.VALIDATION_FAILED.getMessage();
        }
        return build(ErrorCode.VALIDATION_FAILED, detail);
    }

    /** Lỗi validate trên @RequestParam / @PathVariable. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_FAILED, detail.isBlank() ? ErrorCode.VALIDATION_FAILED.getMessage() : detail);
    }

    /** Thiếu tham số bắt buộc. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.INVALID_REQUEST, "Thiếu tham số bắt buộc: " + ex.getParameterName());
    }

    /** Sai kiểu dữ liệu tham số. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.INVALID_REQUEST, "Tham số không hợp lệ: " + ex.getName());
    }

    /** JSON body sai định dạng / không đọc được. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.INVALID_REQUEST, "Nội dung request không hợp lệ");
    }

    /** Vi phạm ràng buộc dữ liệu (unique, foreign key...). Không lộ chi tiết SQL. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DATA_CONFLICT, ErrorCode.DATA_CONFLICT.getMessage());
    }

    /** Sai thông tin đăng nhập do Spring Security ném ra. */
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.AUTH_INVALID, ErrorCode.AUTH_INVALID.getMessage());
    }

    /** Không đủ quyền. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getMessage());
    }

    /**
     * Lưới an toàn cuối cùng: mọi lỗi không lường trước đều trả 500 và
     * chỉ ghi log ở server, KHÔNG trả nội dung lỗi ra ngoài.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không mong đợi tại [{} {}]", request.getMethod(), request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        HttpStatus status = HttpStatus.resolve(errorCode.getCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(new ApiResponse<>(errorCode.getCode(), message, null));
    }
}
