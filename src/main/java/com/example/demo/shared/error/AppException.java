package com.example.demo.shared.error;

/**
 * Ngoại lệ nghiệp vụ. {@link GlobalExceptionHandler} sẽ chuyển thành HTTP status
 * tương ứng với {@link ErrorCode#getCode()}.
 */
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** Cho phép override message khi cần thêm ngữ cảnh (vd: số lượng còn lại). */
    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
