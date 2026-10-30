# Log và audit của ví

## Log kỹ thuật

Ứng dụng ghi JSON theo từng dòng ra console và `logs/application.jsonl`.
File tự xoay theo ngày UTC hoặc khi đạt 20 MB, nén gzip vào `logs/archive`,
giữ tối đa 30 kỳ ngày và giới hạn tổng archive 1 GB. File đang ghi nằm ngoài
giới hạn archive. Cleanup chạy khi xoay file và lúc khởi động; không xóa audit DB.

| Property | Biến môi trường | Mặc định |
|---|---|---|
| `app.logging.directory` | `APP_LOGGING_DIRECTORY` | `logs` |
| `app.logging.max-file-size` | `APP_LOGGING_MAX_FILE_SIZE` | `20MB` |
| `app.logging.max-history` | `APP_LOGGING_MAX_HISTORY` | `30` |
| `app.logging.total-size-cap` | `APP_LOGGING_TOTAL_SIZE_CAP` | `1GB` |

Mount thư mục log ra volume bền vững khi chạy container. Chỉ tài khoản vận hành
được đọc; file log không được phục vụ qua web. Chưa cài hệ thống tập trung bên ngoài.

Không log body, query, cookie hoặc Authorization. HTTP log chỉ gồm method,
route template do Spring xác định, status, durationMs và requestId. Route không
được khớp ghi UNMATCHED. Encoder chỉ nhận các trường context được cho phép,
che credential dạng key/value, Bearer/JWT và giá trị duplicate-entry; exception
chỉ ghi loại và stack frame, bỏ toàn bộ thông báo/cause có thể chứa SQL hoặc token.
Đây là lớp phòng vệ bổ sung: không đưa bí mật vào message, tên nghiệp vụ hay lý do audit.
Không bật SQL bind/HTTP wire logging trong môi trường thật.

Admin đầu tiên chỉ được tạo khi có `ADMIN_INITIAL_PASSWORD`; thiếu cấu hình thì
bỏ qua khởi tạo, không sinh mật khẩu để in log. Cấp mật khẩu bằng secret của môi
trường triển khai, thay sau đăng nhập. Không ghi mật khẩu vào repository.

## Truy vết yêu cầu và tiền

Client có thể gửi `X-Request-Id` gồm 8–80 ký tự A–Z/a–z/0–9/_/-; không hợp lệ
thì server sinh UUID mới. Response luôn trả header này, kể cả 401/403. CORS cho
phép đọc header. ID chỉ dùng tra cứu, không phải khóa idempotency hay bằng chứng xác thực.
MDC được khôi phục khi kết thúc request/worker để tránh lẫn các thread dùng lại.

Audit thao tác, security event và journal lưu requestId. Dữ liệu cũ có thể null.
Bank worker có jobRunId/transferId riêng. Log WALLET_JOURNAL_COMMITTED chỉ được
phát sau commit, kèm journalId, eventKey, currency, walletIds; transactionId hoặc
orderId được gắn cho các event có mã tương ứng. Rollback không phát log thành công.
Journal trong DB là dữ liệu đối soát chính; crash sau commit trước khi ghi file
có thể làm thiếu log kỹ thuật, không làm mất journal.

## Sự kiện bảo mật

Bảng `wallet_security_events` lưu actorId/walletId (có thể null), action, outcome,
code, reference, requestId và createdAt. Không lưu giá trị PIN/OTP/email/token.
Các action gồm thiết lập/đổi/xác minh/khóa PIN, yêu cầu/reset PIN, đăng ký/đổi email
khôi phục, kết quả chuyển email tới SMTP, xác thực thất bại và truy cập bị từ chối.
DELIVERED chỉ có nghĩa SMTP sender hoàn tất, không xác nhận thư đã tới hộp thư.

Sai PIN/OTP và event được ghi trong cùng transaction ngắn với bộ đếm. Lỗi nghiệp
vụ được ném sau commit, nên caller rollback không xóa lịch sử sai. Thay đổi PIN/
email và event thành công cùng commit; rollback không để lại event thành công.
Từ chối ở lớp HTTP 401/403 ghi riêng sau khi xử lý yêu cầu; nếu DB audit lỗi,
response vẫn giữ trạng thái từ chối và phát cảnh báo kỹ thuật để vận hành kiểm tra.
HTTP audit không ghi username nhập sai, IP/X-Forwarded-For hoặc user-agent chưa được xác minh.

## Tra cứu quản trị

- GET `/api/admin/wallet-ops/audit`.
- GET `/api/admin/wallet-ops/security-events`.

Cả hai chỉ dành ADMIN đang hoạt động; hỗ trợ page (từ 1), size (1–100), actorId,
walletId, action, reference, requestId, from, to. Security events thêm outcome
SUCCESS/REJECTED. Khoảng thời gian `[from,to)` dùng ISO LocalDateTime theo giờ
ứng dụng, giống các API ví hiện có; from phải trước to. So khớp action/reference
chính xác, kết quả id giảm dần. Dashboard `/wallet-ops.html` có tab Bảo mật ví
và bộ lọc cho hai loại lịch sử. Dữ liệu audit cũ chưa có walletId không khớp bộ lọc ví.

## Bảo vệ ở database

Chưa chạy schema/trigger/grant trên database ứng dụng. Triển khai bằng tài khoản
migration riêng, dừng phiên bản ứng dụng cũ trong cửa sổ nâng cấp:

1. Với database đúng phiên bản trước thay đổi này, sao lưu và chạy
   `src/main/resources/db/manual/wallet-logging-upgrade.sql` một lần. Nếu Hibernate
   đã tạo cột/bảng, đối chiếu schema và chỉ chạy phần còn thiếu. Không chạy lại ALTER mù.
2. Sau khi schema/backfill đã hoàn tất, chạy `wallet-audit-append-only.sql` một lần.
   Trigger chặn UPDATE/DELETE trên ops audit, security events, status changes,
   journals và journal entries. Không chặn cập nhật các bảng giao dịch/outbox.
3. Tạo role runtime mới bằng DBA, chạy `wallet-runtime-grants.sql` để sinh và duyệt
   GRANT cho database đang chọn. Thực thi các dòng được sinh, gán role và đặt
   DEFAULT ROLE cho tài khoản runtime riêng. Bảng được bảo vệ chỉ có SELECT/INSERT;
   các bảng còn lại có SELECT/INSERT/UPDATE/DELETE. Cấp thêm quyền bảng khi có migration mới.
4. Runtime không được có quyền toàn cục/database-wide bao phủ UPDATE/DELETE,
   hoặc ALTER/DROP/TRIGGER/CREATE/GRANT. MySQL cộng quyền, không có table DENY
   để ghi đè quyền rộng hơn. Không dùng root/migration account chạy ứng dụng.
   Đặt `spring.jpa.hibernate.ddl-auto=validate` sau migration.

Script được đóng gói để DBA áp dụng có chủ đích, không tự chạy lúc ứng dụng khởi
động. TRUNCATE cần DROP nên bị chặn bởi quyền runtime. DBA vẫn có khả năng thay
schema/trigger; đây không phải lưu trữ chống can thiệp tuyệt đối. Sao lưu và lưu
trữ audit ngoài DB bằng tài khoản vận hành riêng theo chính sách triển khai.
Không có API sửa/xóa audit hay tự dọn lịch sử tài chính.

Kiểm thử dùng MySQL tách biệt, thử UPDATE/DELETE bằng SQL trực tiếp và tài khoản
test giới hạn quyền, sau đó dọn tài khoản test. Không thay quyền tài khoản ứng dụng.
