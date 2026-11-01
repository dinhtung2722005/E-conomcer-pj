# Swagger / OpenAPI

Dự án sử dụng `springdoc-openapi-starter-webmvc-ui` 3.2.0 với Spring Boot 4.1.
Phiên bản được chọn theo [bảng tương thích chính thức](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).

## Mở tài liệu

1. Cấu hình database/JWT bằng `.env` hoặc biến môi trường theo README.
2. Chạy profile `dev`: `./mvnw spring-boot:run` khi `.env` có `SPRING_PROFILES_ACTIVE=dev`.
   Trên Windows có thể dùng `./mvnw.cmd spring-boot:run`.
3. Mở [Swagger UI](http://localhost:8080/swagger-ui.html).

| Đường dẫn | Nội dung |
|---|---|
| `/swagger-ui.html` | Chuyển tới giao diện Swagger |
| `/v3/api-docs` | Toàn bộ đặc tả OpenAPI dạng JSON |
| `/v3/api-docs.yaml` | Toàn bộ đặc tả dạng YAML |
| `/v3/api-docs/all` | Tất cả API nghiệp vụ |
| `/v3/api-docs/users` | Đăng nhập, tài khoản, hồ sơ, địa chỉ và quản trị người dùng |
| `/v3/api-docs/ecommerce` | Danh mục, sản phẩm, kho, giỏ hàng, đơn hàng, voucher, flash sale |
| `/v3/api-docs/wallet` | Ví, PIN, nạp/rút, thanh toán, hoàn tiền, quỹ/công nợ, vận hành |

Nhóm được phân theo controller sở hữu API. Ví dụ `/api/orders/{id}/pay`
thuộc `wallet`, còn `/api/orders/checkout` thuộc `ecommerce`.
`/api/users` được mô tả cùng alias `/api/admin/users`.

## Đăng nhập và thử API

1. Trong nhóm `users` hoặc `all`, mở `POST /api/auth/login` → **Try it out**.
2. Điền username/password của tài khoản thử nghiệm và bấm **Execute**.
3. Sao chép `result.accessToken` trong response.
4. Bấm **Authorize**, điền token vào `bearerAuth` rồi xác nhận; không thêm chữ `Bearer`.
5. Chọn endpoint khác, điền path/query/body theo schema và gọi thử.

Swagger tự thêm header `Authorization: Bearer <token>`. Tài liệu cũng mô tả
cookie HttpOnly `jwt` là một cách xác thực thay thế; sau đăng nhập cùng origin,
trình duyệt tự gửi cookie. Không thể nhập thủ công cookie HttpOnly trong hộp Authorize.
Header Bearer được máy chủ ưu tiên hơn cookie; khi token hết hạn, cập nhật token trong
Authorize hoặc xóa giá trị cũ để dùng phiên cookie mới.

`POST /api/auth/refresh` nhận cookie `refresh_jwt` hoặc header `Refresh-Token`.
Refresh token không được trả trong JSON của API đăng nhập; khi dùng Swagger cùng origin,
trình duyệt dùng cookie đã được máy chủ thiết lập. Mỗi lần refresh sẽ thay refresh token.

API xem danh mục/sản phẩm/flash sale và các endpoint xác thực là công khai.
Các API còn lại yêu cầu đăng nhập; `/api/admin/**` và `/api/users/**` yêu cầu ADMIN.
Các giới hạn sở hữu tài nguyên và PIN của ví vẫn được kiểm tra khi gọi từ Swagger.

## Lưu ý khi thử ví

- Nạp/rút qua tài khoản chính chủ đã liên kết và xác minh. Hiện chưa có đối tác ngân hàng
  được chọn; API phụ thuộc đối tác có thể trả HTTP 503 nếu adapter chưa cấu hình.
- Các request nạp, rút, thanh toán, cấp vốn quỹ và trả công nợ cần `Idempotency-Key`.
  Sinh UUID mới cho một thao tác mới; khi retry, gửi lại cùng khóa và cùng nội dung nghiệp vụ.
  Không dùng lại khóa cho một khoản tiền/đơn hàng khác.
- Số tiền nạp/rút là VND nguyên dương. Hạn mức áp dụng theo cấu hình máy chủ;
  ví dụ minh họa trong schema không phải hạn mức cố định.
- Thanh toán và các thao tác nhạy cảm yêu cầu PIN ví 6 chữ số, độc lập mật khẩu đăng nhập.
- Tiền nạp chỉ cộng sau xác nhận đối tác; tiền rút được giữ khi chờ xử lý.
  Thanh toán đơn hàng giữ tiền trước quyết toán người bán.
- Dùng tài khoản và dữ liệu thử nghiệm khi bấm Execute: endpoint thực hiện nghiệp vụ thật
  trên database đang kết nối. Đổi mật khẩu/khôi phục PIN thu hồi phiên và cần đăng nhập lại.

## Quy ước dữ liệu

- Tham số phân trang hiển thị riêng: `page=1`, `size=10`, tối đa 100.
- Enum hiển thị danh sách giá trị; ngày giờ dùng định dạng ISO theo schema và từng API.
- Thành công và lỗi đều bọc trong `{code, message, result}`. Lỗi có `result=null`;
  schema `ApiError` mô tả response chung. `X-Request-Id` dùng để liên kết với log.
- HTTP status lấy từ response thực tế. Hiện `POST /api/orders/checkout` trả HTTP 200
  với trường `code=201`; tài liệu giữ đúng hành vi này.
- Ảnh đại diện gửi `multipart/form-data` với trường `file`, tối đa theo cấu hình upload.

## Bật/tắt theo môi trường

| Cấu hình | Mặc định |
|---|---|
| Profile `dev` | Bật tài liệu |
| Profile `prod` và các môi trường khác | Tắt tài liệu |
| `APP_API_DOCS_ENABLED=true` | Bật Swagger UI và đặc tả, cho phép GET công khai trên đường dẫn tài liệu |
| `APP_API_DOCS_ENABLED=false` | Tắt cả hai và từ chối truy cập, kể cả người dùng ADMIN |

Cấu hình được đọc lúc khởi động. Khi tài liệu bật, quy tắc xác thực và phân quyền
API nghiệp vụ giữ nguyên. Swagger không lưu token Authorize qua lần tải lại trang
và đã tắt dịch vụ validator ngoài; tài nguyên giao diện được phục vụ từ ứng dụng.

## Cập nhật khi thêm endpoint

Thêm `@Tag` cho controller và `@Operation` cho phương thức để mô tả nghiệp vụ.
Request/response và validation được springdoc suy ra từ kiểu DTO và annotation Spring.
`@Schema` bổ sung ý nghĩa trường, ví dụ và cách dùng phân trang/tiền/PIN.
Dùng `@ParameterObject` trên DTO bộ lọc/phân trang để từng trường hiển thị thành query parameter.
Không dùng mật khẩu/token/PIN thật làm ví dụ.

`OpenApiConfiguration` quản lý nhóm, hai cách xác thực, lỗi chung và mô tả Idempotency-Key.
Khi thêm đường dẫn công khai, cập nhật quy tắc tài liệu để khớp `SecurityConfig`.

Kiểm thử Swagger chạy HTTP trên cổng ngẫu nhiên và database MySQL kiểm thử riêng:

```bash
./mvnw -Dtest=OpenApiIntegrationTests,OpenApiDisabledIntegrationTests,ModuleArchitectureTests test
```

Các kiểm tra bao gồm giao diện/tài nguyên, JSON/YAML, độ bao phủ route,
schema request/response, phân nhóm, metadata xác thực và quyền truy cập khi bật/tắt tài liệu.
