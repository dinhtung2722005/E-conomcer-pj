package com.example.demo.user.exception;
public enum ErrorCode {
    USER_NOT_FOUND(404, "User not found"),
    INVALID_REQUEST(400, "Invalid request"),
    INTERNAL_SERVER_ERROR(500, "Internal server error"),
    UNCAUGHT_EXCEPTION(500, "Uncaught exception"),
    USERNAME_INVALID(400, "Username is invalid"),
    PASSWORD_INVALID(400, "Password is invalid"),
    AUTH_INVALID(401, "Tài khoản hoặc mật khẩu không đúng"),
    USERNOTFOUND(404, "Không tìm thấy user"),
    TOKEN_INVALID(401, "Token không hợp lệ"),
    INVALID_KEY(400, "Invalid key"),
    USER_LOCKED(400, "Tài khoản của bạn đã bị khóa"),
    Imgaes_Ivalid(400,"Lỗi hệ thống khi upload ảnh"),
    Product_Not_Found(400,"Không tìm thấy sản phầm"),
    Category_Not_Found(400,"Không tìm thấy danh mục"),
    Flash_Sale_Not_Found(400,"Không tìm thấy chương trình khuyến mãi"),
    Order_Not_Found(400,"Không tìm thấy hóa đơn");
    
    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}