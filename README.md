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
| `ADMIN_INITIAL_PASSWORD` (biến môi trường) | bỏ qua tạo admin nếu thiếu | Mật khẩu admin ban đầu; không in ra log |

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
└── wallet/     PIN, nạp/rút, thanh toán/hoàn, quyết toán, sổ đối ứng và đối soát
```

## Quy ước API

- Mọi response thành công bọc trong `ApiResponse`: `{ "code": 200, "message": "...", "result": ... }`.
- Lỗi trả về **HTTP status thật** (400/401/403/404/409/500) kèm cùng cấu trúc `ApiResponse`,
  `result = null`. Không lộ stack trace.
- Xác thực bằng JWT: gửi qua cookie `jwt` (trình duyệt) **hoặc** header `Authorization: Bearer <token>` (client khác).
- Làm mới phiên: `POST /api/auth/refresh`, gửi cookie `refresh_jwt` hoặc header `Refresh-Token`. Refresh token **dùng một lần** — mỗi lần làm mới sẽ bị thay bằng token mới.
- Đăng nhập và refresh tạo cả hai token trong cùng transaction. Hai request dùng cùng
  một refresh token chỉ có một request thành công; nếu ký access token lỗi, rotation được rollback.
- Đổi mật khẩu hoặc khóa tài khoản tăng `users.token_version` và xóa refresh token
  trong cùng transaction. Mọi access token cũ bị từ chối ngay, kể cả sau khi mở khóa lại.
  Hibernate `ddl-auto=update` hiện có sẽ thêm cột `token_version BIGINT DEFAULT 0 NOT NULL`
  khi khởi động lại. Token cũ chưa có claim này được xem là phiên bản 0.

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

### API quản trị

CRUD người dùng dùng đường dẫn chuẩn `/api/admin/users`: `POST` tạo (HTTP 201),
`GET /{id}`, `PUT /{id}` cập nhật email và `DELETE /{id}`. `/api/users` tiếp tục
là alias cho cùng các thao tác, bộ lọc và cấu trúc response. Các response hồ sơ
(đăng nhập, hồ sơ cá nhân, CRUD và danh sách người dùng) có trường boolean `isActive`.

Thay `PUT /api/admin/users/{id}/toggle-status` bằng
`PUT /api/admin/users/{id}/status` với body bắt buộc:

```json
{ "isActive": false }
```

Response trả hồ sơ người dùng sau cập nhật. Gửi lại cùng trạng thái không đảo
trạng thái hoặc thu hồi phiên thêm lần nữa. Khóa tài khoản thu hồi toàn bộ phiên;
mở khóa không khôi phục token cũ. Không cho phép khóa tài khoản ADMIN.

Các API `GET` danh sách sau nhận chung `?page=1&size=10`:

| Đường dẫn | Bộ lọc thêm |
|---|---|
| `/api/admin/users` (alias `/api/users`) | `keyword`, `isActive`, `role=ADMIN\|SELLER\|CUSTOMER` |
| `/api/admin/categories` | — |
| `/api/admin/products` | — |
| `/api/admin/inventories` | — |
| `/api/admin/vouchers` | — |
| `/api/admin/orders` | — |
| `/api/admin/flash-sales` | — |
| `/api/admin/flash-sales/active` | — |
| `/api/admin/flash-sales/{flashSaleId}/items` | — |

Trang bắt đầu từ **1**, mặc định `size=10`, tối đa **100**; page/size không hợp lệ
hoặc role không thuộc enum trả HTTP 400. Kết quả sắp theo `id` giảm dần.
`keyword` tìm username/email, bỏ khoảng trắng hai đầu, không phân biệt hoa thường;
`%`, `_`, `!` được tìm như ký tự thường. Flash sale không tồn tại trả 404 khi lấy items.

**Frontend cần cập nhật:** danh sách quản trị nay nằm ở `response.result.data`
thay vì mảng `response.result` ở các danh sách chưa phân trang trước đây:

```json
{
  "code": 200,
  "message": "Thành công",
  "result": {
    "currentPage": 1,
    "totalPages": 0,
    "totalElements": 0,
    "data": []
  }
}
```

Ví dụ trên là danh sách rỗng. Trang vượt quá tổng số trang cũng trả mảng rỗng và
vẫn giữ `currentPage` đã yêu cầu. Các API khách hàng giữ quy ước phân trang hiện có.

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

Các thao tác thanh toán/hủy khóa dòng đơn hàng trước khi đọc trạng thái. Request hủy
lặp lại trả về đơn `CANCELLED`, không hoàn kho/voucher lần nữa. Checkout và hoàn kho
khóa sản phẩm theo thứ tự ID. Job quá hạn kiểm tra lại `PENDING` và thời gian tạo sau
khi khóa, nên không hủy đơn vừa được thanh toán.

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

### Ví nội bộ

Đã có API khởi tạo/đọc ví, lịch sử giao dịch, khoản giữ và tự khóa tại `/api/wallet`;
ADMIN khóa/mở khóa tại `/api/admin/wallets/{id}/freeze` và `/unfreeze`.
Tất cả cần đăng nhập, nhóm admin yêu cầu ROLE_ADMIN. Xem [API ví](docs/wallet-api.md)
để biết query, body và response. Nạp/rút/thanh toán/PIN chưa mở endpoint.

## Chưa hoàn thiện

Các phần sau đã có entity/DTO nhưng **chưa có nghiệp vụ** — cần làm tiếp nếu muốn dùng:

- `wallet/` — tự tạo ví khi đăng ký, quản lý/PIN, nạp/rút có idempotency và outbox,
  thanh toán/hủy nguyên tử với kho/voucher, duyệt hoàn, quyết toán người bán,
  cashback cấu hình được và journal/đối soát. Chưa cài adapter ngân hàng tiền thật.
  DTO đầu vào/response cho nạp/rút, thanh toán, hoàn tiền và PIN đã được chuẩn bị theo kế hoạch.
  Xem [mô hình ví và yêu cầu migration](docs/wallet-model.md) trước khi triển khai
  database có dữ liệu, và [chính sách quyết toán/vận hành](docs/wallet-operations.md).
  [Các bổ sung sau rà soát](docs/wallet-improvements.md) mô tả dashboard
  `/wallet-ops.html`, khôi phục PIN email, quản lý ngân hàng, thông báo và hoàn
  một phần/công nợ sau quyết toán. SMTP cần cấu hình trước khi gửi mã thật.
- Khôi phục mật khẩu qua OTP — `OtpCode`, `OtpCodeRepository` và `EmailService` (file đang comment toàn bộ) chưa được nối vào luồng nào.
- Xác minh email khi đăng ký.
- Thanh toán trực tuyến: đơn chỉ chuyển `PENDING → PAID` thủ công qua API admin, chưa có cổng thanh toán.
- CSRF: hiện tắt, bù lại cookie phiên đặt `SameSite=Strict`. Nếu tách frontend sang domain khác, phải bật CSRF
  (`CookieCsrfTokenRepository`) trong `SecurityConfig`.

## Kiểm thử

### Lỗi Lombok trong VS Code

Nếu Maven build thành công nhưng VS Code báo `Flexible Constructor Bodies` tại
`@RequiredArgsConstructor`, `@AllArgsConstructor` hoặc `@NoArgsConstructor`, kiểm tra
extension **Language Support for Java by Red Hat**. Phiên bản `1.57.0` dùng JDT có
[lỗi tương thích với constructor do Lombok sinh](https://github.com/projectlombok/lombok/issues/4086).

Cách khắc phục đã kiểm tra với dự án Java 21 này:

1. Chọn **Install Another Version...** của extension và cài `1.56.0` (hoặc chạy
   `code --install-extension redhat.java@1.56.0 --force`). Tạm tắt **Auto Update** cho
   riêng extension này đến khi bản mới tích hợp bản sửa của JDT.
2. Giữ `java.jdt.ls.lombokSupport.enabled` là `true` trong settings của workspace.
3. Chạy **Java: Clean Java Language Server Workspace**, rồi khởi động lại VS Code
   khi được yêu cầu để bỏ các chẩn đoán cũ.

Nếu còn lỗi `variable ... not initialized in the default constructor` do Oracle
Java báo, extension này dùng `nb-javac` dựa trên JDK 27. Dự án dùng Lombok
`1.18.48` vì [phiên bản này hỗ trợ JDK 27](https://projectlombok.org/changelog).
Sau khi Maven tải dependency mới, chạy **Developer: Reload Window** để extension
nạp lại phiên bản Lombok của dự án.

Lombok dependency và annotation processor dùng chung `${lombok.version}` trong
`pom.xml`. Maven bật annotation processing tường minh và giữ
`lombok-mapstruct-binding` để MapStruct thấy getter/setter do Lombok sinh.

### Chạy test

```bash
./mvnw test
```

Test cần MySQL đang chạy và tài khoản DB hiện tại có quyền `CREATE DATABASE` / `DROP DATABASE`.
Kiểm thử phân quyền audit còn cần `CREATE USER`, `DROP USER` và quyền cấp SELECT/INSERT
trên database kiểm thử; tài khoản test giới hạn được tạo tạm rồi dọn trong finally.
Mỗi Spring test context tạo database riêng `ecommerce_test_<UUID>`, tạo bảng trong đó,
và tự xóa database kiểm thử khi đóng context. Các test không sửa bảng/dữ liệu trong
database ứng dụng đang cấu hình.

`CriticalFlowsIntegrationTests` kiểm tra trên MySQL thật: tranh chấp tồn kho và giỏ hàng,
hủy/thanh toán đồng thời, giới hạn voucher/flash sale, rollback khi checkout lỗi,
tính tiền giảm giá, kiểm tra lại đơn quá hạn, refresh token dùng một lần và rollback,
thu hồi phiên khi đổi mật khẩu/khóa tài khoản, cập nhật hồ sơ đồng thời, quyền sở hữu
đơn hàng, cùng luồng HTTP admin login/refresh/logout. Các test API quản trị kiểm tra
phân trang trên mọi danh sách, tham số sai, bộ lọc user, phân quyền, CRUD qua đường
dẫn chuẩn/alias và cập nhật trạng thái lặp lại. `DemoApplicationTests` kiểm tra khởi động context.

## Log và audit

Xem [log JSON, requestId, lịch sử bảo mật và SQL bảo vệ audit](docs/wallet-logging.md).
