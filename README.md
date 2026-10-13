# E-commerce Backend (demo)

Backend REST API cho hệ thống bán hàng, viết bằng **Spring Boot 4.1** / **Java 21** / **MySQL**.

## Chạy dự án

```bash
# 1. Tạo database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ecommerce CHARACTER SET utf8mb4;"

# 2. Chạy
./mvnw spring-boot:run
```

Ứng dụng chạy ở `http://localhost:8080`. Mặc định kết nối `localhost:3306/ecommerce` với `root/123456`.

### Cấu hình

Toàn bộ cấu hình nằm trong `src/main/resources/application.properties` — sửa trực tiếp file đó là chạy được, không cần set biến môi trường.

| Property | Mặc định | Ghi chú |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/ecommerce...` | |
| `spring.datasource.username` / `password` | `root` / `123456` | |
| `app.jwt.secret` | chuỗi Base64 384-bit | **Đổi giá trị này là toàn bộ token đang phát hành mất hiệu lực** |
| `app.jwt.expiration-ms` | `900000` (15 phút) | Thời gian sống access token |
| `app.jwt.refresh-expiration-ms` | `86400000` (24 giờ) | Thời gian sống refresh token; đổi thành `604800000` nếu muốn 7 ngày |
| `cloudinary.*` | — | Thiếu/sai thì chỉ chức năng upload avatar bị tắt |
| `app.cors.allowed-origins` | `http://localhost:3000,http://localhost:5173,http://localhost:8080` | Danh sách origin của frontend, phân tách bằng `,` |
| `app.order.pending-timeout-minutes` | `15` | Đơn `PENDING` quá hạn này sẽ bị tự hủy và hoàn kho |
| `ADMIN_INITIAL_PASSWORD` (biến môi trường) | sinh ngẫu nhiên, in ra log | Mật khẩu admin tạo ở lần chạy đầu, khi chưa có tài khoản nào |

> **Khi deploy lên môi trường thật:** biến môi trường có độ ưu tiên cao hơn `application.properties`, nên có thể ghi đè mà không phải sửa code. Tên biến là dạng viết hoa + gạch dưới của property — ví dụ `app.jwt.secret` → `APP_JWT_SECRET`, `cloudinary.api-secret` → `CLOUDINARY_API_SECRET`. Xem `.env.example`.

> **Lưu ý bảo mật:** các giá trị trong `application.properties` (mật khẩu DB, JWT secret, Cloudinary secret) đang nằm trong repo. Nếu repo là công khai, hãy đổi các giá trị này trước khi dùng thật, hoặc chuyển sang biến môi trường như trên.

## Kiến trúc

```
com.example.demo
├── user/       tài khoản, xác thực JWT, địa chỉ, hồ sơ
│   ├── security/   JwtUtils, JwtAuthenticationFilter, SecurityConfig, UserPrincipal
│   ├── service/    AuthService, UserService, RefreshTokenService, CurrentUserService, ...
│   └── job/        TokenCleanupJob (dọn blacklist)
├── ecommerce/  danh mục, sản phẩm, kho, giỏ hàng, đơn hàng, voucher, flash sale
│   └── job/        OrderCleanupJob, FlashSaleStatusJob
└── wallet/     entity ví điện tử (chưa có service/controller — xem "Chưa hoàn thiện")
```

## Quy ước API

- Mọi response thành công bọc trong `ApiResponse`: `{ "code": 200, "message": "...", "result": ... }`.
- Lỗi trả về **HTTP status thật** (400/401/403/404/409/500) kèm cùng cấu trúc `ApiResponse`,
  `result = null`. Không lộ stack trace.
- Xác thực bằng JWT: gửi qua cookie `jwt` (trình duyệt) **hoặc** header `Authorization: Bearer <token>` (client khác).
- Làm mới phiên: `POST /api/auth/refresh`, gửi cookie `refresh_jwt` hoặc header `Refresh-Token`. Refresh token **dùng một lần** — mỗi lần làm mới sẽ bị thay bằng token mới.

### Phân quyền

| Nhóm đường dẫn | Yêu cầu |
|---|---|
| `POST /api/auth/{register,login,refresh,logout}` | công khai |
| `GET /api/categories/**`, `GET /api/products/**`, `GET /api/flash-sales/**` | công khai |
| `/api/admin/**`, `/api/users/**` | `ROLE_ADMIN` |
| Còn lại (`/api/carts/**`, `/api/orders/**`, `/api/vouchers/**`, `/api/details/**`, `/api/addresses/**`) | đã đăng nhập |

> **Quan trọng — thay đổi so với bản cũ:**
> Các API của khách **không còn nhận `userId` trên query string**. Người dùng được xác định từ JWT.
> Frontend cần bỏ tham số `?userId=...` ở: `/api/carts/**`, `/api/orders/checkout`, `/api/vouchers/save`, `/api/vouchers/my-vouchers`.

### Flash sale

Trạng thái chương trình (`UPCOMING` / `ONGOING` / `ENDED`) được **hệ thống tự suy ra từ khung thời gian**,
không nhận từ request body. Một job chạy mỗi phút để đồng bộ.

Hạn mức (`totalQuantity`) được thực thi thật: khi đặt hàng, suất khuyến mãi được giữ chỗ và
`soldQuantity` tăng lên. Nếu suất đã hết, sản phẩm tự động quay về giá gốc.

### Voucher

- Voucher phải được **lưu vào ví trước** (`POST /api/vouchers/save`) mới dùng được lúc thanh toán.
- Sau khi dùng, voucher được đánh dấu đã dùng và biến mất khỏi `GET /api/vouchers/my-vouchers`.
- Hủy đơn sẽ **hoàn lại** lượt voucher cho cả hệ thống lẫn ví người dùng.
- `DiscountType.PERCENTAGE` được giới hạn bởi `maxDiscountAmount`; `DiscountType.FIXED_AMOUNT` giảm số tiền cố định.

### Vòng đời tồn kho

| Sự kiện | Thay đổi |
|---|---|
| Đặt hàng | `available -= n`, `locked += n` |
| Đơn chuyển sang `PAID` | `locked -= n` (hàng rời kho vĩnh viễn) |
| Đơn bị hủy (từ `PENDING`) | `locked -= n`, `available += n` |
| Đơn bị hủy (từ `PAID`) | `available += n` |

### Vòng đời đơn hàng

```
PENDING ──> PAID ──> SHIPPED ──> COMPLETED
   │          │
   └──────────┴──> CANCELLED
```

Các bước chuyển khác bị từ chối (400 `ORDER_STATUS_INVALID`).

## Endpoint chính

**Xác thực & hồ sơ** — `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`,
`GET|PUT /api/details/profile`, `PUT /api/details/password`, `POST /api/details/avatar`, `/api/addresses/**`

**Khách hàng** — `GET /api/products`, `GET /api/products/{id}`, `GET /api/categories`,
`GET /api/flash-sales/current`, `/api/carts/**`, `POST /api/orders/checkout`,
`GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel`,
`GET /api/vouchers`, `GET /api/vouchers/validate`, `POST /api/vouchers/save`, `GET /api/vouchers/my-vouchers`

**Quản trị** (`ROLE_ADMIN`) — `/api/admin/categories/**`, `/api/admin/products/**`,
`/api/admin/inventories/**`, `/api/admin/flash-sales/**`, `/api/admin/flash-sale-items/{id}`,
`/api/admin/vouchers/**`, `/api/admin/orders/**`, `/api/admin/users/**`, `/api/users/**`

> Đường dẫn voucher admin đã đổi từ `/api/admin/voucher` (số ít) sang `/api/admin/vouchers` (số nhiều) cho nhất quán.

## Chưa hoàn thiện

Các phần sau đã có entity/DTO nhưng **chưa có nghiệp vụ** — cần làm tiếp nếu muốn dùng:

- `wallet/` — ví điện tử (`Wallet`, `WalletTransaction`): chưa có repository/service/controller.
- Khôi phục mật khẩu qua OTP — `OtpCode`, `OtpCodeRepository` và `EmailService` (file đang comment toàn bộ) chưa được nối vào luồng nào.
- Xác minh email khi đăng ký.
- Thanh toán trực tuyến: đơn chỉ chuyển `PENDING → PAID` thủ công qua API admin, chưa có cổng thanh toán.
- CSRF: hiện tắt, bù lại cookie phiên đặt `SameSite=Strict`. Nếu tách frontend sang domain khác, phải bật CSRF
  (`CookieCsrfTokenRepository`) trong `SecurityConfig`.

## Kiểm thử

```bash
./mvnw test
```

> Hiện chỉ có một test `contextLoads()` và **cần MySQL đang chạy**. Các phần rủi ro cao chưa có test:
> tranh chấp tồn kho khi đặt hàng đồng thời, tính tiền giảm giá, hạn mức flash sale, job hủy đơn quá hạn.
