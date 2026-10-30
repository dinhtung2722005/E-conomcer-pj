# Tích hợp ngân hàng

Chưa chọn đối tác: ứng dụng không cài BankGateway, không có cổng giả trong main
source. Liên kết/nạp/rút trả 503 khi chưa có adapter. Gateway mô phỏng chỉ nằm
trong kiểm thử. Không có API cho khách tự xác nhận thành công.

## Hợp đồng adapter

Đăng ký đúng một bean BankGateway. `verifyOwnership(userId, oneTimeProof)` phải
xác minh bằng chứng một lần từ đối tác, ràng buộc với tài khoản đang đăng nhập
và quyền sở hữu ngân hàng. Không nhận `verified=true` hay số tài khoản tự khai
để đánh dấu đã xác minh. Chỉ lưu token đối tác và thông tin đã che.

`resolve(command)` phải tra cứu trước theo merchantReference; nếu chưa có thì
gửi bằng cùng reference và cơ chế idempotency của đối tác. Gửi lại sau crash
không được chuyển lần hai. Adapter phải xác thực nguồn trả lời, chữ ký, người
nhận, số tiền và loại tiền. Nếu không đảm bảo gửi/tra cứu an toàn, chưa bật adapter.
REJECTED chỉ dùng cho kết quả thất bại cuối cùng, không còn khả năng chi tiền.
Timeout, dữ liệu thiếu hoặc chưa rõ kết quả phải PROCESSING hoặc throw.

## Lưu và đối soát

1. Transaction DB tạo WalletOperation, WalletTransaction và BankTransfer outbox.
   Rút chuyển CASH khả dụng sang pending và tạo hold, không rút BONUS.
2. Worker claim bằng lease 2 phút và commit. HTTP ngân hàng nằm ngoài transaction.
3. Worker khóa ví, giao dịch, hold rồi outbox, so khớp reference/số tiền/tiền tệ/
   token. Thành công nạp cộng CASH; thành công rút capture hold; rút bị từ chối
   cuối cùng release hold. Trạng thái cuối cùng không bị áp dụng lần hai.
4. Timeout giữ tiền, chuyển PROCESSING, retry exponential backoff tối đa 1 giờ
   giữa hai lần. Lease hết hạn cho phép tiếp tục sau crash bằng cùng reference.
   Quét theo ID tăng dần, không tăng offset trên tập đang thay đổi.

Chưa có webhook công khai: worker dùng kết quả tra cứu từ adapter. Chỉ thêm webhook
khi biết giao thức/chữ ký của đối tác, và dùng cùng luồng kiểm chứng kết quả.
Không tự FAILED khi quá số lần thử hoặc hết hạn QR nếu ngân hàng chưa xác nhận.

## Sử dụng API

POST nạp/rút yêu cầu `Idempotency-Key`: 1–80 ký tự ASCII chữ, số, `.`, `_`, `:`, `-`.
Giữ cùng key cho cùng một thao tác; khác payload dùng key mới. Cùng key khác
loại giao dịch/số tiền/tài khoản trả 409; PIN không được lưu trong fingerprint.
Số tiền 100 và 100.00 được coi như nhau. Response đầu tiên thường PENDING;
poll GET `/api/wallet/top-ups/{transactionId}` để nhận hướng dẫn nạp/trạng thái,
hoặc GET `/api/wallet/withdrawals/{transactionId}` cho rút. Mỗi API chỉ đọc ví của tôi.

Cấu hình worker: `wallet.bank.worker-enabled` (true), `wallet.bank.poll-delay-ms`
(30000), `wallet.bank.initial-delay-ms` (30000). Không có adapter thì worker bỏ qua.
Theo dõi các outbox có `last_error`, thời gian chờ và số lần thử trước khi vận hành.
