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
  wallet.ops.refund-sla; khoản giữ mặc định 9 ngày qua wallet.ops.hold-sla.
  Worker tạo cảnh báo trùng khóa một lần, lưu lần ghi nhận
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

## Chặng 5 — hoàn một phần và bù trừ sau quyết toán

POST `/api/orders/{id}/refund` nhận:

```json
{
  "requestKey": "return-order-123-item-1",
  "reason": "Đã kiểm tra hàng lỗi",
  "items": [{"orderItemId": 10, "quantity": 1}]
}
```

Không truyền `items` nghĩa là hoàn toàn bộ số lượng chưa hoàn. Tiền/phí được tính
từ snapshot đã lưu, không nhận amount do khách tự đặt. Từ chối dòng hàng không
thuộc đơn, trùng dòng hoặc vượt số lượng còn lại. Cùng requestKey khác payload
trả 409; gửi lại cùng payload trả cùng hồ sơ. Nếu bỏ key để tương thích API cũ,
server dùng hash reason/items làm key; muốn tạo hồ sơ mới phải dùng key mới.
Một đơn có thể có nhiều hồ sơ đã xử lý nhưng chỉ một hồ sơ REQUESTED tại một lúc.

Duyệt qua POST `/api/admin/orders/{id}/refund/decision` với
`{caseId,approved,reason}`. Khi đơn có nhiều hồ sơ phải chỉ rõ caseId. Dashboard
luôn gửi caseId. Chỉ duyệt mới thay đổi tiền/kho, từ chối không làm biến động tiền.
Trả kho đúng số lượng; chỉ hoàn voucher và chuyển REFUNDED khi đã trả toàn bộ
số lượng. Đơn trả một phần vẫn COMPLETED, quyết toán phần còn lại sau hạn.

Hoàn trước giải ngân release đúng CASH/BONUS gốc; quyết toán chỉ capture phần
còn giữ, trả người bán/phí theo phân bổ sau hoàn. BONUS không biến thành CASH.
Thanh toán tự mua sản phẩm của chính mình bị từ chối để tránh chuyển BONUS sang
CASH qua quyết toán giả.

Khách giữ thời hạn 7 ngày. ADMIN có thể tạo hồ sơ ngoại lệ bằng POST
`/api/admin/orders/{id}/refund`, cùng body ở trên, kể cả sau hạn hoặc sau quyết
toán. Đây mới là bước tạo hồ sơ; vẫn phải duyệt riêng và kiểm tra nguồn tiền.

Hoàn sau quyết toán không release lại hold đã capture:

1. Trừ phần tiền thuần phải hoàn từ CASH khả dụng của từng người bán và đảo phần
   phí sàn đã ghi nhận; không làm ví người bán âm.
2. Phần thiếu chỉ lấy từ WalletRefundReserve đã được cấp tiền thật từ CASH có
   sẵn trong ví ADMIN. Không có API cộng số dư quỹ tùy ý. Quỹ thiếu trả 409, mọi
   debit/refund/công nợ rollback, hồ sơ vẫn REQUESTED.
3. Số quỹ ứng cho người bán được ghi WalletRecoveryDebt nguồn CASH; quyết toán
   sau tự thu hồi từ khoản người bán sắp được nhận và trả lại quỹ. Có hai giao
   dịch SETTLEMENT/DEBT_REPAYMENT để lịch sử phản ánh đúng số tiền thực nhận.
4. Hoàn về CASH/BONUS theo tỷ lệ nguồn gốc và phần còn được hoàn; lần cuối nhận
   phần dư để tổng hoàn không vượt PAYMENT gốc.
5. Nếu trước đó có cashback, thu hồi BONUS tương ứng. BONUS đã tiêu tạo công nợ
   BONUS, tự trừ vào lần thưởng sau, không ép số dư âm. Cashback mặc định vẫn 0.

API quỹ và công nợ:

- GET `/api/admin/wallet-ops/refund-reserve?currency=VND`.
- POST `/api/admin/wallet-ops/refund-reserve/fund`: header Idempotency-Key và body
  `{amount,pin,reason}`. ADMIN chuyển CASH của chính mình vào quỹ, có journal/audit.
- GET `/api/admin/wallet-ops/debts`: danh sách quản trị; tab Công nợ trên dashboard.
- GET `/api/wallet/debts`: chỉ công nợ của tôi.
- POST `/api/wallet/debts/repay`: `{amount,pin,reason}` và Idempotency-Key, trả
  công nợ CASH từ CASH khả dụng. Không được trả quá nợ. Ví còn nợ CASH không rút
  ra ngân hàng được; lệnh rút cũ chưa gửi cũng bị suspend, có thể hủy để trả nợ.
  Lệnh đã gửi chỉ được tra cứu kết quả. Vẫn có thể bù trừ nợ từ các lần quyết toán sau.

## Nâng cấp database có dữ liệu

Chưa chạy thay đổi schema/backfill trên database ứng dụng. Trước khi triển khai:

- Tạo bảng cursor, alert, audit, PIN recovery, notifications, refund lines,
  refund reserves và recovery debts theo entity; thêm các cột retry/security/
  notificationPublished/refundedAmount/settledAmount và revokedAt/dispatchStartedAt.
- Bỏ unique cũ chỉ trên wallet_refund_cases.order_id, thay bằng unique
  (order_id,request_key) và giữ index cho khóa ngoại. Thêm request_key,
  fingerprint, amount, fee_amount, bonus_recovery_amount cho hồ sơ cũ.
- Bản cũ chỉ hoàn toàn bộ đơn: đối soát rồi dựng refund lines từ snapshot gốc
  cho hồ sơ cũ; không tự suy ra từ giá/chủ sản phẩm hiện tại. Nếu thiếu snapshot,
  xử lý dữ liệu trước khi mở API duyệt. Backfill refundedAmount và settledAmount
  từ các nghiệp vụ đã hoàn/giải ngân thực tế, không cộng lại tiền.
- Liên kết cũ có attempts>0 nhưng chưa có dispatchStartedAt được coi là có khả
  năng đã gửi, không được hủy/release tự động. Ví PIN cũ chưa có recoveryEmail
  phải đăng ký bằng PIN hiện tại rồi xác minh OTP.
- Quỹ hoàn khởi tạo 0, không backfill số dư giả. Tiền OPENING của ví cũ vẫn cần
  đối soát theo tài liệu mô hình; chạy migration trên bản sao trước khi triển khai.

Kiểm thử dùng MySQL riêng, mail capture và gateway test; không gửi email/chuyển
tiền thật. Chưa cài adapter ngân hàng, SMTP mặc định tắt. Các phần này không nằm
trong thay đổi kết nối ngân hàng ở chặng 2 chưa được chọn.
