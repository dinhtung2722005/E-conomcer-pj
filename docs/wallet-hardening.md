# Hoàn thiện ví sau đợt rà soát

## Hành vi đã sửa

- Worker đọc liên kết ngân hàng bằng `PESSIMISTIC_WRITE` sau khóa user/wallet/transaction/transfer.
  Thu hồi đã commit trước lần đọc này sẽ chặn lệnh chưa gửi. Lệnh đã bắt đầu dispatch
  vẫn chỉ được đối soát; không tự release khi chưa biết kết quả ngân hàng.
- Dashboard quyết toán hiển thị `finalAmount - refundedAmount`.
- Ví còn nợ CASH không tạo thanh toán mua hàng mới, kể cả chọn BONUS. Rút tiền và
  lệnh rút chưa gửi tiếp tục bị chặn. Retry một thanh toán đã thành công trả kết quả
  cũ. Nhận tiền, hủy đơn, hoàn tiền và trả nợ vẫn được phép.
- Khi nạp thành công, CASH khả dụng được dùng trả nợ trước; phần còn lại mới có thể
  chi tiêu. Giao dịch nạp, trả nợ, tăng quỹ hoàn và journal cùng commit/rollback.

## Hạn mức đã chốt

| Loại yêu cầu | Tối đa mỗi lần | Tối đa mỗi ngày |
|---|---:|---:|
| Nạp | 50.000.000 VND | 100.000.000 VND |
| Rút | 20.000.000 VND | 50.000.000 VND |
| Thanh toán đơn | 50.000.000 VND | 100.000.000 VND |

Mỗi ví, mỗi loại có tối đa 10 yêu cầu mới trong cửa sổ trượt 10 phút. Ngày tính theo
`Asia/Ho_Chi_Minh`; chuyển ngày không xóa giới hạn của cửa sổ trượt. Tổng thanh toán
tính cả CASH và BONUS. Nạp/rút VND phải là số nguyên; phân bổ nội bộ, hoàn tiền và
journal vẫn giữ 4 chữ số để không mất phần dư. Đơn miễn phí vẫn tính một yêu cầu.

`wallet_limit_reservations` ghi số tiền và thời điểm UTC của yêu cầu trong cùng
transaction nghiệp vụ. Giữ khóa ví và dùng current read để hai request không cùng
vượt phần hạn mức còn lại. Lỗi làm rollback toàn bộ reservation. Retry cùng khóa
idempotency không tính thêm; trạng thái ngân hàng pending/failed/cancelled và đơn
hoàn sau đó không giải phóng quota đã commit. Cách tính này ngăn tạo/hủy liên tục
để vượt hạn mức. Chỉ dùng bảng này cho giới hạn nghiệp vụ, không thay sổ tiền.

Các property nằm dưới `wallet.limits.*`; biến môi trường tương ứng có trong
`.env.example`. Nếu dùng thêm tiền tệ thật ngoài VND, cần chính sách hạn mức riêng
cho tiền tệ đó trước khi mở cho người dùng.

## Scheduler và ngân hàng

Các nhóm có scheduler riêng: ngân hàng 1 luồng, quyết toán 1, thông báo 1,
đối soát/SLA 2 và bảo trì chung 2. Ngân hàng bị chậm không chiếm luồng quyết toán
hoặc thu hồi phiên.

`WalletBankClient` dùng tối đa 4 lời gọi ngoài, không có hàng đợi tăng vô hạn;
thời gian chờ mặc định 10 giây. Timeout luôn giữ kết quả tiền ở trạng thái chưa rõ,
giữ hold và retry cùng merchant reference. Không suy ra FAILED chỉ vì timeout.
Adapter thật phải có connect/read timeout riêng và hỗ trợ idempotent submit/lookup;
hủy Future không bảo đảm ngân hàng đã hủy lệnh. Những lời gọi bỏ qua interrupt
vẫn chiếm một slot và không được tạo thread thay thế vô hạn.

## Cấu hình bí mật và triển khai

File công khai chỉ chứa placeholder. `.env` local bị Git bỏ qua và không đi vào JAR.
Biến môi trường thật có độ ưu tiên cao hơn file local. `.env.example` là mẫu, không
chứa khóa dùng thật. Artifact mặc định chọn `prod`; local chọn `dev` qua `.env`.

Profile `prod` yêu cầu tài khoản DB riêng, từ chối root, credential trong JDBC URL,
JWT Base64 ngắn hơn 32 byte, Hibernate khác `validate` hoặc Flyway bật trong runtime.
Runtime không cần DDL. DBA cấp quyền theo `db/manual/wallet-runtime-grants.sql` sau
migration; quota, journal và audit chỉ có SELECT/INSERT, không cấp quyền ghi lịch
sử Flyway. Không cấp thêm quyền toàn schema/global vì quyền MySQL cộng dồn.

Local đã chuyển thông tin DB vào `.env` và sinh JWT key mới; access token cũ mất
hiệu lực sau lần khởi động tiếp theo, client cần refresh hoặc đăng nhập lại.
Khóa Cloudinary cũ đã bị loại khỏi cấu hình đang
dùng. Chủ tài khoản cần thu hồi/thay khóa tại Cloudinary rồi cấu hình khóa mới nếu
muốn dùng upload ảnh. Thay mật khẩu DB ở nơi triển khai và secret store; thay file
không đổi mật khẩu tại server. Không sửa lịch sử Git tự động.

## Migration có phiên bản

Flyway chạy bằng tài khoản migration trong bước triển khai riêng. Không tự chạy
migration bằng tài khoản runtime hoặc tự baseline database đã có dữ liệu.

| Version | Nội dung |
|---|---|
| V1 | Schema đầy đủ cho database trống, gồm các khóa ngoại chéo module |
| V2 | Bảng quota và backfill các yêu cầu hiện có từ operation/transaction |
| V3 | Tạo các trigger append-only còn thiếu; giữ nguyên trigger đã tồn tại |

CI/máy triển khai cấp `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` qua biến môi
trường thật. Maven không tự đọc `.env`. Chạy từ thư mục chứa pom.xml:

```sh
# Database trống, hoặc database đã có Flyway history hợp lệ:
mvn -Pdatabase-migrations compile flyway:migrate
```

Database cũ chưa có history: sao lưu, kiểm tra trên bản sao rằng schema khớp bản ví
trước thay đổi này, hoàn tất các backfill/ràng buộc cũ còn thiếu, rồi baseline tường
minh ở version 1. Baseline không kiểm tra hay sửa schema cũ thay cho người vận hành:

```sh
mvn -Pdatabase-migrations compile flyway:baseline
mvn -Pdatabase-migrations compile flyway:migrate
```

V2 mặc định hiểu `created_at` cũ là giờ Việt Nam `+07:00` và chuyển sang UTC.
Nếu JVM trước đây ghi giờ UTC, phải đặt `-Dflyway.placeholders.legacyWalletTimeZone=+00:00`
khi migrate. Yêu cầu không có transaction để suy ra thời điểm (ví dụ đơn miễn phí
cũ) được tính từ thời điểm migration để không tạo lỗ hổng quota. Số tiền pending
đã có cũng được tính, tránh vượt hạn mức ngay sau nâng cấp.

Các migration đã triển khai phải được giữ nguyên; thay đổi tiếp theo tạo version
mới. Kiểm tra checksum Flyway sẽ phát hiện script/guard resource bị sửa. Lệnh clean
bị tắt. Chưa chạy migration trên database ứng dụng trong đợt sửa này.

Tham khảo: [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html),
[Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline).

## Kiểm thử

Kiểm thử dùng database `ecommerce_test_<UUID>` riêng, bao gồm thu hồi liên kết giữa
lúc worker đang đọc route, timeout và pool bị chiếm, scheduler độc lập, quota pending/
retry/rollback/đồng thời/nửa đêm, chặn mua khi có nợ và tự thu nợ sau nạp.
Migration được kiểm tra cho schema trống và schema cũ đã baseline, backfill thời
gian/số tiền, chạy lại không thay đổi và Hibernate validate trên schema đã migrate.
CI chạy integration test cần cấp thông tin MySQL test, quyền tạo/xóa database test
và `SPRING_PROFILES_ACTIVE=dev`; không dùng tài khoản runtime production cho bộ test.
