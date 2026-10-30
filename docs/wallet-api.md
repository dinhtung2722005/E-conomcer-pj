# API quản lý ví

Ví được tạo cùng transaction đăng ký; tài khoản cũ có thể gọi POST để khởi tạo.
PIN có API thiết lập/đổi và trạng thái bảo mật. Nạp/rút có nghiệp vụ và hàng đợi
ngân hàng; cần cài BankGateway của đối tác để giao dịch tiền thật.

## Xác thực và response

Tất cả API yêu cầu đăng nhập bằng JWT, qua `Authorization: Bearer <accessToken>`
hoặc cookie `jwt` theo cấu hình xác thực hiện có. Nhóm `/api/admin/wallets/**`
chỉ dành cho ADMIN, kiểm tra cả ở URL, controller và service.

Response giữ cấu trúc `ApiResponse<T>`:

```json
{
  "code": 200,
  "message": "Thành công",
  "result": {}
}
```

HTTP status khớp `code`. Các thao tác thành công dưới đây trả 200; POST khởi
tạo ví là idempotent theo user, trả 200 cả khi mới tạo và khi ví đã tồn tại.

## Endpoint đã triển khai

| Method | Path | Đầu vào | `result` |
|---|---|---|---|
| POST | `/api/wallet` | Không cần body | WalletResponse |
| GET | `/api/wallet` | Không có | WalletResponse |
| GET | `/api/wallet/security` | Không có | WalletSecurityResponse |
| POST | `/api/wallet/pin` | SetupPinRequest | WalletSecurityResponse |
| PUT | `/api/wallet/pin` | ChangePinRequest | WalletSecurityResponse |
| GET | `/api/wallet/bank-accounts` | Không có | List<BankAccountSummaryResponse> |
| POST | `/api/wallet/bank-accounts` | LinkBankAccountRequest | BankAccountSummaryResponse |
| POST | `/api/wallet/top-ups` | TopUpRequest + Idempotency-Key | TopUpResponse |
| GET | `/api/wallet/top-ups/{id}` | ID giao dịch của tôi | TopUpResponse |
| POST | `/api/wallet/withdrawals` | WithdrawRequest + Idempotency-Key | WithdrawResponse |
| GET | `/api/wallet/withdrawals/{id}` | ID giao dịch của tôi | WithdrawResponse |
| POST | `/api/orders/{id}/pay` | PayOrderRequest + Idempotency-Key | PaymentResponse |
| POST | `/api/orders/{id}/confirm-receipt` | Không cần body | OrderResponse |
| POST | `/api/orders/{id}/refund` | RefundCaseRequest | RefundCaseResponse |
| GET | `/api/orders/{id}/refund` | ID đơn của tôi | RefundCaseResponse |
| POST | `/api/admin/orders/{id}/refund/decision` | RefundDecisionRequest | RefundCaseResponse |
| GET | `/api/admin/wallets/{id}/reconciliation` | ID ví | WalletReconciliationResponse |
| GET | `/api/admin/wallet-ops/summary` | ADMIN | Tổng quan vận hành |
| GET | `/api/admin/wallet-ops/{transfers,holds,refunds,settlements,alerts,audit,debts}` | Phân trang ADMIN | Danh sách vận hành |
| POST | `/api/wallet/pin/recovery` | Không body | Challenge/email đã che |
| POST | `/api/wallet/pin/recovery/confirm` | challengeId, code, newPin, confirmPin | Đăng nhập lại sau reset |
| POST | `/api/wallet/security/recovery-email` | pin | Challenge xác minh email |
| POST | `/api/wallet/security/recovery-email/confirm` | challengeId, code | Xác minh email bảo vệ |
| POST | `/api/wallet/bank-accounts/{id}/revoke` | pin, reason | Thu hồi liên kết |
| POST | `/api/wallet/withdrawals/{id}/cancel` | pin, reason | Hủy rút chưa gửi |
| GET | `/api/wallet/notifications` | Phân trang | Thông báo của tôi |
| POST | `/api/wallet/notifications/{id}/read` | Không body | Đánh dấu đã đọc |
| POST | `/api/admin/orders/{id}/refund` | requestKey, reason, items | Hồ sơ ngoại lệ ADMIN |
| GET | `/api/admin/wallet-ops/refund-reserve` | currency | Quỹ hoàn |
| POST | `/api/admin/wallet-ops/refund-reserve/fund` | amount, pin, reason + Idempotency-Key | Cấp quỹ từ CASH ADMIN |
| GET | `/api/wallet/debts` | Phân trang | Công nợ của tôi |
| POST | `/api/wallet/debts/repay` | amount, pin, reason + Idempotency-Key | Trả công nợ từ CASH |
| GET | `/api/wallet/transactions` | WalletHistoryQuery qua query string | PageResponse<WalletTransactionResponse> |
| GET | `/api/wallet/transactions/{id}` | ID giao dịch dương | WalletTransactionResponse |
| GET | `/api/wallet/holds` | WalletHoldQuery qua query string | PageResponse<WalletHoldResponse> |
| POST | `/api/wallet/freeze` | WalletFreezeRequest | WalletResponse |
| POST | `/api/admin/wallets/{id}/freeze` | ID ví dương và WalletFreezeRequest | WalletResponse |
| POST | `/api/admin/wallets/{id}/unfreeze` | ID ví dương và WalletFreezeRequest | WalletResponse |

API khách lấy chủ sở hữu từ tài khoản đang đăng nhập, không có tham số chọn
user/ví khác. GET không tạo ví: chưa khởi tạo thì trả 404 WALLET_NOT_FOUND.
User tự khóa được ví nhưng mở khóa chỉ có đường ADMIN. Ví LOCKED vẫn xem được
số dư/lịch sử; các service chi tiền sau này phải kiểm tra khóa trước khi ghi nợ.

## Bộ lọc lịch sử

Thiết lập PIN yêu cầu mật khẩu đăng nhập hiện tại; đổi PIN yêu cầu PIN cũ và xác
nhận PIN mới. PIN chỉ lưu dưới dạng BCrypt. Mặc định nhập sai 5 lần khóa 15 phút;
cấu hình bằng `wallet.pin.max-attempts` và `wallet.pin.lock-duration` (ISO Duration).
Bộ đếm sai được commit riêng, kể cả khi nghiệp vụ tài chính bị rollback. Không có
API xác minh PIN trả cờ cho phép thanh toán; xác minh diễn ra trong service.

Thanh toán lấy số tiền từ `Order.finalAmount`, chỉ chủ đơn được thanh toán.
`useBonus=true` dùng BONUS trước rồi CASH; không kết hợp thẻ/ngân hàng khi checkout.
Đơn 0 đồng không tạo giao dịch/hold 0 đồng. Tiền chuyển sang pending cho đến quyết
toán; kho được xác nhận và đơn chuyển PAID trong cùng transaction. Admin không
còn được chuyển PENDING sang PAID trực tiếp. Hủy từ khách/admin/job dùng chung
luồng hoàn tiền cùng transaction kho/voucher, trả đúng nguồn CASH/BONUS. Gửi lại
cùng key trả kết quả thanh toán gốc; key mới không thể thanh toán lại đơn đã trả tiền.

Nhận hàng, cửa sổ hoàn 7 ngày, duyệt hoàn, quyết toán và cấu hình cashback/phí
được mô tả tại [vận hành ví](wallet-operations.md). Hiện mặc định phí 0%, cashback
tắt. API hoàn một phần, quỹ/công nợ, cấu hình SMTP và yêu cầu nâng cấp dữ liệu
được mô tả trong [các bổ sung sau rà soát](wallet-improvements.md).
Nạp/rút yêu cầu adapter ngân hàng thật; xem [hợp đồng adapter](wallet-bank-integration.md).

`GET /api/wallet/transactions` nhận:

- `page`: mặc định 1, tối thiểu 1.
- `size`: mặc định 10, từ 1 tới 100.
- `type`: DEPOSIT, WITHDRAW, PAYMENT, REFUND, CASHBACK, SETTLEMENT, CASHBACK_REVERSAL,
  RESERVE_FUNDING, DEBT_REPAYMENT.
- `status`: PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED.
- `from`, `to`: thời gian ISO local datetime, ví dụ `2026-10-19T00:00:00`.
  Khoảng lọc là `[from,to)`, from phải trước to khi cung cấp cả hai.

Ví dụ:

```text
GET /api/wallet/transactions?page=1&size=10&type=DEPOSIT&status=SUCCESS&from=2026-10-19T00:00:00&to=2026-10-20T00:00:00
```

Lịch sử sắp theo `createdAt DESC, id DESC`. `result` gồm `currentPage`,
`totalPages`, `totalElements`, `data`. Giao dịch không thuộc ví hiện tại hoặc
không tồn tại đều trả 404, không tiết lộ dữ liệu ví khác.

## Bộ lọc khoản giữ

`GET /api/wallet/holds` nhận `page`, `size` như lịch sử và `statuses`.
Không truyền statuses thì lấy mọi trạng thái; nếu truyền phải có ít nhất một
giá trị hợp lệ: ACTIVE, PARTIALLY_PROCESSED, CAPTURED, RELEASED, CLOSED.

```text
GET /api/wallet/holds?page=1&size=10&statuses=ACTIVE&statuses=PARTIALLY_PROCESSED
```

Chỉ trả hold của ví hiện tại, sắp theo `createdAt DESC, id DESC`.

## Khóa/mở khóa

Ba endpoint đổi trạng thái đều nhận JSON:

```json
{
  "reason": "Yêu cầu kiểm tra tài khoản"
}
```

Lý do bắt buộc, không chỉ chứa khoảng trắng và tối đa 500 ký tự. Trạng thái và
audit được ghi trong cùng transaction; gửi lại trạng thái hiện có không sinh
thêm một lần chuyển trạng thái. API không thay đổi số dư hoặc tự hủy lệnh rút
đã gửi ngân hàng.

## Lỗi

- 400: query/body sai, ID không dương hoặc sai kiểu.
- 401: chưa xác thực hoặc JWT không còn hợp lệ.
- 403: tài khoản/quyền không đủ, đặc biệt khách truy cập nhóm admin.
- 404: ví/giao dịch không tồn tại trong phạm vi được phép.

Response không chứa entity, PIN/hash PIN, idempotency key, token tài khoản ngân
hàng hay thông báo lỗi raw của đối tác. Trước khi dùng khóa/mở khóa với database
cũ, cần migration bảng `wallet_status_changes` như tài liệu mô hình ví.
