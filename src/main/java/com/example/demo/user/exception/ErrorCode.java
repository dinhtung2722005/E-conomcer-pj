package com.example.demo.user.exception;

/**
 * Danh sách mã lỗi nghiệp vụ.
 *
 * <p>{@code code} là HTTP status code thật sẽ được trả về cho client.
 */
public enum ErrorCode {

    // ===== Lỗi chung =====
    INVALID_REQUEST(400, "Dữ liệu không hợp lệ"),
    INVALID_KEY(400, "Dữ liệu không hợp lệ"),
    VALIDATION_FAILED(400, "Dữ liệu gửi lên không hợp lệ"),
    ENDPOINT_NOT_FOUND(404, "Đường dẫn không tồn tại"),
    FILE_TOO_LARGE(413, "File vượt quá dung lượng cho phép"),
    INTERNAL_SERVER_ERROR(500, "Lỗi hệ thống, vui lòng thử lại sau"),
    UNCAUGHT_EXCEPTION(500, "Lỗi hệ thống, vui lòng thử lại sau"),
    DATA_CONFLICT(409, "Dữ liệu đã tồn tại"),

    // ===== Xác thực / phân quyền =====
    AUTH_INVALID(401, "Tài khoản hoặc mật khẩu không đúng"),
    UNAUTHENTICATED(401, "Bạn cần đăng nhập để thực hiện thao tác này"),
    ACCESS_DENIED(403, "Bạn không có quyền thực hiện thao tác này"),
    TOKEN_INVALID(401, "Token không hợp lệ hoặc đã hết hạn"),
    REFRESH_TOKEN_NOT_FOUND(401, "Không tìm thấy Refresh Token"),

    // ===== Người dùng =====
    USER_NOT_FOUND(404, "Không tìm thấy người dùng"),
    USER_LOCKED(403, "Tài khoản của bạn đã bị khóa"),
    USERNAME_EXISTED(409, "Tên đăng nhập đã tồn tại"),
    EMAIL_EXISTED(409, "Email đã được sử dụng"),
    USERNAME_INVALID(400, "Tên đăng nhập không hợp lệ"),
    PASSWORD_INVALID(400, "Mật khẩu không hợp lệ"),
    OLD_PASSWORD_INCORRECT(400, "Mật khẩu cũ không chính xác"),
    PASSWORD_CONFIRM_MISMATCH(400, "Mật khẩu xác nhận không khớp"),
    PASSWORD_UNCHANGED(400, "Mật khẩu mới không được giống mật khẩu cũ"),
    AVATAR_INVALID(400, "File ảnh không hợp lệ, chỉ chấp nhận ảnh JPG/PNG/WEBP dưới 5MB"),
    AVATAR_UPLOAD_FAILED(500, "Lỗi hệ thống khi upload ảnh"),
    ADMIN_CANNOT_BE_LOCKED(400, "Không thể khóa tài khoản quản trị viên"),
    CANNOT_DELETE_SELF(400, "Bạn không thể tự xóa tài khoản của mình"),

    // ===== Địa chỉ =====
    ADDRESS_NOT_FOUND(404, "Không tìm thấy địa chỉ"),
    ADDRESS_FORBIDDEN(403, "Bạn không có quyền thay đổi địa chỉ này"),
    ADDRESS_DEFAULT_CANNOT_DELETE(400, "Không thể xóa địa chỉ mặc định. Vui lòng chọn địa chỉ khác làm mặc định trước khi xóa"),
    ADDRESS_DEFAULT_REQUIRED(400, "Vui lòng chọn một địa chỉ khác làm mặc định trước"),

    // ===== Danh mục / sản phẩm =====
    CATEGORY_NOT_FOUND(404, "Không tìm thấy danh mục"),
    CATEGORY_IN_USE(400, "Danh mục đang được sản phẩm sử dụng, không thể xóa"),
    PRODUCT_NOT_FOUND(404, "Không tìm thấy sản phẩm"),
    PRODUCT_NOT_ACTIVE(400, "Sản phẩm đã ngừng kinh doanh"),
    PRODUCT_HAS_ORDERS(400, "Sản phẩm đã phát sinh đơn hàng, chỉ có thể ngừng kinh doanh"),
    PRODUCT_NAME_EXISTED(409, "Tên sản phẩm đã tồn tại"),

    // ===== Kho =====
    INVENTORY_NOT_FOUND(400, "Sản phẩm chưa có dữ liệu tồn kho"),
    OUT_OF_STOCK(400, "Số lượng tồn kho khả dụng không đủ"),
    INVENTORY_INCONSISTENT(500, "Dữ liệu tồn kho không nhất quán"),

    // ===== Giỏ hàng =====
    CART_NOT_FOUND(404, "Không tìm thấy giỏ hàng"),
    CART_EMPTY(400, "Giỏ hàng đang trống, không thể đặt hàng"),
    CART_ITEM_NOT_FOUND(404, "Sản phẩm không tồn tại trong giỏ hàng"),
    INVALID_QUANTITY(400, "Số lượng phải là số nguyên lớn hơn 0"),

    // ===== Đơn hàng =====
    ORDER_NOT_FOUND(404, "Không tìm thấy đơn hàng"),
    ORDER_NOT_OWNED(403, "Bạn không có quyền truy cập đơn hàng này"),
    ORDER_STATUS_INVALID(400, "Không thể chuyển đơn hàng sang trạng thái này"),

    // ===== Ví nội bộ =====
    WALLET_NOT_FOUND(404, "Không tìm thấy ví"),
    WALLET_TRANSACTION_NOT_FOUND(404, "Không tìm thấy giao dịch ví"),
    WALLET_PIN_NOT_CONFIGURED(409, "Vui lòng thiết lập mã PIN ví"),
    WALLET_PIN_ALREADY_CONFIGURED(409, "Ví đã có mã PIN"),
    WALLET_PIN_INCORRECT(400, "Mã PIN không chính xác"),
    WALLET_PIN_LOCKED(429, "Mã PIN đang bị khóa tạm thời, vui lòng thử lại sau"),
    WALLET_PIN_UNCHANGED(400, "Mã PIN mới phải khác mã PIN hiện tại"),
    WALLET_PIN_AUTHORIZATION_EXPIRED(409, "Vui lòng xác thực lại mã PIN"),
    WALLET_LOCKED(403, "Ví đang bị khóa chi tiêu và rút tiền"),
    WALLET_INSUFFICIENT_BALANCE(409, "Số dư ví không đủ"),
    WALLET_IDEMPOTENCY_CONFLICT(409, "Khóa yêu cầu đã được dùng với nội dung khác"),
    WALLET_BANK_UNAVAILABLE(503, "Chưa cấu hình kết nối ngân hàng"),
    WALLET_BANK_ACCOUNT_NOT_FOUND(404, "Không tìm thấy tài khoản ngân hàng đã xác minh"),
    WALLET_REFUND_NOT_FOUND(404, "Không tìm thấy yêu cầu hoàn hàng"),

    // ===== Voucher =====
    VOUCHER_NOT_FOUND(404, "Mã giảm giá không tồn tại"),
    VOUCHER_CODE_EXISTED(409, "Mã giảm giá đã tồn tại"),
    VOUCHER_EXPIRED(400, "Mã giảm giá đã hết hạn hoặc chưa đến thời gian sử dụng"),
    VOUCHER_USAGE_LIMIT(400, "Mã giảm giá đã hết lượt sử dụng"),
    VOUCHER_MIN_ORDER(400, "Đơn hàng chưa đạt giá trị tối thiểu để sử dụng mã này"),
    VOUCHER_NOT_SAVED(400, "Bạn chưa lưu mã giảm giá này vào ví"),
    VOUCHER_ALREADY_SAVED(400, "Bạn đã lưu mã giảm giá này rồi"),
    VOUCHER_ALREADY_USED(400, "Mã giảm giá này đã được sử dụng"),
    VOUCHER_IN_USE(400, "Không thể xóa mã giảm giá đã được sử dụng"),
    VOUCHER_INVALID_CONFIG(400, "Cấu hình mã giảm giá không hợp lệ"),

    // ===== Flash sale =====
    FLASH_SALE_NOT_FOUND(404, "Không tìm thấy chương trình khuyến mãi"),
    FLASH_SALE_ITEM_NOT_FOUND(404, "Không tìm thấy sản phẩm trong chương trình khuyến mãi"),
    FLASH_SALE_ITEM_EXISTED(409, "Sản phẩm này đã tồn tại trong chương trình khuyến mãi"),
    FLASH_SALE_PRICE_INVALID(400, "Giá khuyến mãi phải nhỏ hơn giá gốc của sản phẩm"),
    FLASH_SALE_TIME_INVALID(400, "Thời gian kết thúc phải sau thời gian bắt đầu"),
    FLASH_SALE_QUANTITY_INVALID(400, "Số lượng khuyến mãi không hợp lệ"),
    FLASH_SALE_PRODUCT_IMMUTABLE(400, "Không được phép thay đổi sản phẩm gốc. Vui lòng xóa và tạo mới"),
    FLASH_SALE_EXCEEDS_STOCK(400, "Số lượng khuyến mãi vượt quá tồn kho hiện có"),
    FLASH_SALE_SOLD_OUT(400, "Sản phẩm đã hết suất khuyến mãi");

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
