# Các bổ sung sau rà soát ví

## Chặng 1 — kiểm soát dòng tiền

- PIN grant ràng buộc User.tokenVersion; transaction chi tiền khóa lại user trước
  order/wallet, từ chối account bị khóa hoặc phiên đã thu hồi giữa hai bước.
- Từ chối thanh toán khi thiếu snapshot seller, phân bổ hoặc ví nhận cùng tiền tệ.
  Checkout không chấp nhận sản phẩm chưa gán seller. Admin tạo user cũng tạo ví.
- Ledger từ chối entry không biểu diễn chính xác bằng DECIMAL(19,4), trước khi lưu.
- Worker tiếp tục cursor qua nhiều lượt, không quét lặp mãi 1.000 bản ghi đầu.
- Rút chưa bắt đầu gửi bị suspend khi ví/account bị khóa. Đã bắt đầu gửi chỉ được
  lookup khi khóa, không gọi resolve có khả năng tạo giao dịch mới. Adapter muốn
  tra cứu ở trạng thái này phải triển khai BankGateway.lookup thật sự chỉ đọc.
- dispatchStartedAt được commit trước khi gọi ngân hàng. Nếu không biết chắc đã
  gửi hay chưa, không hủy/release hold. Gateway thật vẫn chưa được cài.

Chính sách giữ nguyên: phí 0%, cashback tắt, cửa sổ hoàn hàng 7 ngày.

## Chặng 3 — vận hành

Trang `/wallet-ops.html` dùng JWT ADMIN hoặc cookie đăng nhập. Trang chỉ là shell;
mọi API `/api/admin/wallet-ops/**` kiểm tra ADMIN và trạng thái user trong DB.
Token nhập trên trang chỉ giữ trong bộ nhớ, không lưu localStorage.

- GET summary, transfers, holds, refunds, settlements, alerts, audit; danh sách
  phân trang page>=1, size<=100. transfers hỗ trợ status/overdue, refunds và alerts
  lọc status. Thông tin tài khoản ngân hàng/token/hash không được trả ra.
- POST transfers/{id}/retry và settlements/{id}/retry nhận `{reason}`: chỉ lên
  lịch worker theo reference cũ, không tự đánh dấu thành công hay sửa số dư.
- POST alerts/{id}/review nhận `{status,reason}`. Có lịch sử người thao tác/lý do.
- SLA nạp/rút và hồ sơ hoàn mặc định 48h, cấu hình wallet.ops.transfer-sla và
  wallet.ops.refund-sla. Worker tạo cảnh báo trùng khóa một lần, lưu lần ghi nhận
  gần nhất; kiểm tra thêm tổng journal mất cân bằng và chênh lệch ví/sổ.
- Cursor lưu trong DB, claim một lô trước xử lý, nhiều worker không claim cùng
  lô tại cùng thời điểm. Crash có thể lặp xử lý khi quét vòng, các nghiệp vụ vẫn
  kiểm tra idempotency/trạng thái. Quyết toán lỗi có backoff và cảnh báo riêng.

## Chặng 4 — bảo mật và tiện ích

- POST `/api/wallet/pin/recovery`: gửi mã 8 số tới email khôi phục đã lưu riêng
  khi thiết lập PIN. Thay đổi email hồ sơ không đổi địa chỉ này.
- POST `/api/wallet/pin/recovery/confirm`: `{challengeId,code,newPin,confirmPin}`.
  Mã chỉ dùng một lần, hiệu lực 10 phút, tối đa 5 lần sai; mỗi ví tối đa 3 yêu cầu
  mỗi giờ và cách nhau ít nhất 60 giây. Bộ đếm sai commit độc lập.
- PIN cũ chưa có email khôi phục: POST `/api/wallet/security/recovery-email`
  với `{pin}`, rồi `/confirm` với `{challengeId,code}` để xác minh email hồ sơ.
  Không tự lấy email hồ sơ mới thay cho email khôi phục được bảo vệ.
- Reset thành công thu hồi mọi phiên, yêu cầu đăng nhập lại và tạm chặn rút 24h
  (`wallet.recovery.withdrawal-cooldown`). Worker cũng kiểm tra cooldown.
- Gửi thật cần `wallet.recovery.email-enabled=true`, `wallet.recovery.email-from`
  và cấu hình `spring.mail.host/port/username/password`, TLS theo SMTP. Mặc định
  chưa cấu hình: trả 503; không có mã dự phòng qua response/log. Test dùng mail
  capture chỉ trong src/test, không gửi email ra ngoài.
- POST `/api/wallet/bank-accounts/{id}/revoke` và
  `/api/wallet/withdrawals/{transactionId}/cancel` nhận `{pin,reason}`. Thu hồi
  không xóa lịch sử; không cho tạo yêu cầu mới bằng token đã thu hồi. Hủy rút chỉ
  được khi chưa có lần gửi/attempt; release hold và ghi journal/audit đúng một lần.
- GET `/api/wallet/notifications` và POST `/{id}/read`: thông báo trong ứng dụng,
  chỉ chủ ví đọc/đánh dấu. Worker xuất sự kiện giao dịch cuối cùng từ DB, mỗi
  transaction một thông báo; retry không gửi trùng. Thay đổi bảo mật được lưu
  thông báo cùng transaction. Không gửi email giao dịch hàng loạt.
