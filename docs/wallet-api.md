# API quản lý ví

Các API này gọi WalletService hiện có. Nạp/rút, thanh toán đơn và thiết lập/đổi
PIN chưa có service/API. Khởi tạo ví chưa tự chạy trong luồng đăng ký.

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

`GET /api/wallet/transactions` nhận:

- `page`: mặc định 1, tối thiểu 1.
- `size`: mặc định 10, từ 1 tới 100.
- `type`: DEPOSIT, WITHDRAW, PAYMENT, REFUND, CASHBACK, SETTLEMENT, CASHBACK_REVERSAL.
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
