# Kế hoạch service và DTO ví nội bộ

Trạng thái: đã triển khai quản lý ví, PIN, hook đăng ký, nạp/rút qua outbox,
thanh toán/hủy, hồ sơ hoàn toàn bộ đơn, quyết toán theo snapshot người bán,
cashback cấu hình được và sổ đối ứng/đối soát. Chưa có adapter ngân hàng thật,
webhook theo giao thức đối tác hoặc sinh trắc học. Đã bổ sung khôi phục PIN qua
email, vận hành/cảnh báo và hoàn một phần/bù trừ sau giải ngân; xem
[tài liệu bổ sung](wallet-improvements.md) để biết trạng thái mới nhất.
Chính sách đã chọn: phí 0%, cashback tắt, cửa sổ hoàn hàng 7 ngày.
Xem [API ví](wallet-api.md), [kết nối ngân hàng](wallet-bank-integration.md)
và [quyết toán/vận hành](wallet-operations.md) để biết hành vi hiện tại.
Dựa trên luồng ngân hàng → ví → thanh toán đơn → quyết toán/hoàn.

## 1. Phạm vi và lựa chọn ban đầu

- Ngân hàng chỉ tham gia khi nạp/rút; checkout không thanh toán kết hợp ngân hàng.
- Giữ `CheckoutService.placeOrder()` tạo đơn `PENDING` và giữ kho/voucher như hiện
  tại. Thêm thao tác thanh toán ví cho đơn đó. Thiếu tiền thì đơn vẫn `PENDING`
  và tiếp tục tuân theo thời hạn hủy hiện có; người mua nạp thêm rồi thanh toán lại.
- Thanh toán thành công nghĩa là tiền đã chuyển vào hold và đơn thành `PAID`.
  Tiền chưa được giải phóng cho người bán ở bước này.
- Tách bước đặt đơn/thanh toán ở backend; giao diện có thể điều phối hai bước
  liên tiếp. Chưa gộp thành một transaction checkout dài trong bản đầu.
- Dùng PIN cho bản đầu. Sinh trắc học, khôi phục PIN và tăng mức xác thực được
  bổ sung khi có cơ chế xác minh phù hợp, không giả lập bằng cờ từ frontend.
- API ví lấy người dùng qua `UserAccountsApi`; response dùng `ApiResponse<T>`
  và `PageResponse<T>` hiện có. Không trả trực tiếp entity JPA.

## 2. Các service

| Thành phần | Trách nhiệm | Các thao tác dự kiến |
|---|---|---|
| `WalletService` | Khởi tạo ví, đọc số dư/lịch sử/khoản giữ, đóng băng ví theo quyền | `initializeWallet`, `getMyWallet`, `getHistory`, `getTransaction`, `getHolds`, `freezeWallet`, `unfreezeWallet` |
| `WalletSecurityService` | Thiết lập/đổi PIN, hash/xác minh PIN, giới hạn nhập sai | `setupPin`, `changePin`, `verifyForOperation`, `getSecurityStatus` |
| `WalletFundingService` | Điều phối nạp/rút và kết quả ngân hàng | `createTopUp`, `requestWithdrawal`, `handleVerifiedBankEvent` |
| `OrderPaymentApplicationService` / `WalletPaymentService` | Điều phối thanh toán qua API; ví giữ CASH/BONUS theo snapshot đã khóa | `payOrder` / `authorizePayment`, `reserve` |
| `WalletRefundService` | Hoàn đơn đã trả tiền, hoàn một phần, trả đúng nguồn tiền | `refundCancelledOrder`, `refundApprovedReturn` |
| `WalletSettlementService` | Quyết toán khoản giữ, doanh thu người bán và phí sàn | `settleCompletedOrder` |
| `WalletReconciliationService` | Tra cứu kết quả chưa rõ và kiểm tra sai lệch | `reconcileBankOperations`, `reconcileHeldBalances` |
| `WalletCashbackService` (giai đoạn mở rộng) | Cấp/thu hồi thưởng theo chính sách đủ điều kiện | `grantCashback`, `reverseCashback` |

Các phần hỗ trợ không mở thành API điều chỉnh số dư tự do:

- `WalletFundsService`: thao tác nội bộ giữ/capture/release/credit/debit đúng
  ngăn tiền. Bắt buộc đã có transaction (`MANDATORY`) và khóa phù hợp từ luồng
  gọi; không tự commit tách khỏi đơn hoặc giao dịch. Chưa tạo trước nhiều lớp
  abstraction nếu các thao tác thực tế chưa cần dùng chung.
- `BankGateway`: interface tạo yêu cầu, tra cứu và kiểm chứng thông báo đối tác;
  adapter cụ thể chỉ được chọn khi xác định ngân hàng/cổng thanh toán. Adapter
  giả dùng cho test không được mở endpoint cộng tiền giả trên môi trường thật.
- Các thao tác DB của nạp/rút và thanh toán đặt trong bean transaction riêng
  hoặc `TransactionTemplate`; tránh gọi nội bộ cùng bean rồi trông chờ
  `@Transactional` tự có hiệu lực.
- `WalletMapper`, `WalletTransactionMapper`, `WalletHoldMapper`: chuyển entity
  sang DTO theo cách dùng mapper hiện có của dự án; không lộ hash/PIN/token ngân hàng.

## 3. DTO đầu vào

Đặt trong `com.example.demo.wallet.api.dto`, gom theo nghiệp vụ nếu số file tăng.
Các thao tác thay đổi tiền nhận `Idempotency-Key` từ header; khóa này không phải
mật khẩu và không thay thế xác thực. Khóa của nghiệp vụ tự động được backend sinh
từ sự kiện/case gốc, không lấy tùy ý từ người gọi.

| DTO | Trường dự kiến | Quy tắc |
|---|---|---|
| `TopUpRequest` | `amount`, `linkedBankAccountId` | Số tiền dương; tài khoản thuộc user và đủ điều kiện nạp theo đối tác |
| `WithdrawRequest` | `amount`, `linkedBankAccountId`, `pin` | Chỉ CASH khả dụng; xác minh chủ sở hữu tài khoản nhận |
| `PayOrderRequest` | `pin`, `useBonus` | `orderId` ở path; backend lấy tổng tiền từ đơn; `useBonus` mặc định false |
| `SetupPinRequest` | `currentPassword`, `newPin`, `confirmPin` | PIN đúng 6 chữ số; xác thực lại người thiết lập; hai PIN khớp |
| `ChangePinRequest` | `currentPin`, `newPin`, `confirmPin` | Kiểm tra PIN cũ và trạng thái khóa; hai PIN mới khớp |
| `WalletHistoryQuery` | `page`, `size`, `type`, `status`, `from`, `to` | Trang bắt đầu 1, size tối đa 100; khoảng `[from,to)`, from < to khi đủ hai đầu |
| `WalletHoldQuery` | `page`, `size`, `statuses` | Chỉ đọc hold của ví đã xác định từ user |
| `WalletFreezeRequest` | `reason` | User chỉ tự khóa; mở khóa cần luồng xác minh/quyền quản trị phù hợp |

Không nhận `userId`, số dư sau thao tác, trạng thái giao dịch, người bán nhận tiền
hoặc số tiền thanh toán đơn từ request khách hàng. `linkedBankAccountId` vẫn phải
được kiểm tra quyền sở hữu, không tin chỉ vì đó là ID do backend từng cấp.

PIN/mật khẩu là trường chỉ ghi khi đọc JSON, không ghi log/toString, không nằm
trong idempotency fingerprint hoặc outbox. Validate PIN/confirmPin và số tiền
bằng Bean Validation kết hợp kiểm tra nghiệp vụ. Dùng BigDecimal, thống nhất
quy tắc làm tròn với checkout hiện đang làm tròn 2 chữ số; không tự làm tròn
request thành một số tiền khác mà người dùng chưa xác nhận.

## 4. DTO đầu ra

| DTO | Nội dung |
|---|---|
| `WalletResponse` | `id`, `currency`, bốn số dư, `status`, `pinConfigured`, thời điểm cập nhật |
| `WalletTransactionResponse` | `id`, `referenceId`, `type`, `balanceType`, `amount`, `status`, `originalTransactionId`, lỗi công khai đã chuẩn hóa, các mốc thời gian |
| `WalletHoldResponse` | `id`, `orderId`, `referenceId`, loại/nguồn/trạng thái, tiền giữ ban đầu, đã capture/release, còn lại |
| `TopUpResponse` | `transactionId`, trạng thái, số tiền/tiền tệ và hướng dẫn nạp đã chuẩn hóa khi đối tác cung cấp |
| `WithdrawResponse` | `transactionId`, `holdId`, số tiền, trạng thái, thông tin tài khoản nhận đã che |
| `PaymentResponse` | `orderId`, tổng tiền, phần CASH/BONUS, danh sách `transactionIds`/`holdIds`, trạng thái đơn |
| `RefundResponse` | Mã case hoàn, orderId, phần CASH/BONUS đã hoàn, các transactionId và trạng thái |
| `WalletSecurityResponse` | `pinConfigured`, `pinLockedUntil`, `pinChangedAt` |

Các DTO trong hai bảng đã có trong `wallet/api/dto`. `PaymentResponse` dùng
`orderStatus` cho trạng thái đơn, danh sách ID mặc định rỗng để hỗ trợ đơn 0 đồng.
`TopUpInstructionsResponse` chứa thông tin tài khoản nhận tiền/nội dung chuyển
khoản/QR do adapter chuẩn hóa; `BankAccountSummaryResponse` chỉ chứa thông tin
đích rút đã che số tài khoản. Service phải tạo dữ liệu che trước khi gán response.
Các DTO nạp/rút/thanh toán/PIN chưa có endpoint hay nghiệp vụ tương ứng.

Không trả `pinHash`, PIN thô, token tài khoản ngân hàng, raw callback hoặc stacktrace.
Response lịch sử là `PageResponse<WalletTransactionResponse>`; lịch sử giữ thứ tự
`createdAt DESC, id DESC`, tránh sort từ PageQuery vô tình thay đổi thứ tự này.

Các command nội bộ có kiểu riêng, không bind trực tiếp request HTTP:

- `RefundCommand`: case hoàn đã được duyệt, orderId, lý do; số tiền/phân bổ được
  lấy từ dữ liệu hoàn hàng đã duyệt, không tin request khách tự khai.
- `SettlementCommand`: orderId và sự kiện đủ điều kiện quyết toán; không nhận
  sellerId/feeAmount tùy ý từ khách.
- `CashbackCommand`: orderId và ruleId; service tính phần thưởng từ rule đã lưu.
- `VerifiedBankEvent`: kết quả chuẩn hóa sau xác minh chữ ký/nguồn gốc và đối
  chiếu số tiền, loại tiền, merchant reference. Shape callback thực tế phụ thuộc
  adapter; không lấy body `status=SUCCESS` làm bằng chứng đã nhận tiền.

## 5. Ranh giới transaction và các luồng

### PIN

Xác minh trước khi mở transaction đang giữ khóa tiền/đơn. Cập nhật số lần nhập
sai/thời gian khóa trong transaction ngắn riêng. Trả kết quả xác minh sau khi
transaction đó commit rồi mới ném lỗi ra ngoài nếu cần; không để lỗi PIN làm
rollback chính bộ đếm nhập sai.

Kết quả thành công là ngữ cảnh nội bộ gắn với user, loại thao tác và dữ liệu
được xác nhận; không chấp nhận cờ `pinVerified` từ client. Khi khóa dữ liệu để
ghi tiền phải kiểm tra lại chủ sở hữu, trạng thái và số tiền còn khớp. Không giữ
khóa ví rồi gọi transaction PIN riêng quay lại khóa chính ví đó.

### Thanh toán

1. Kiểm tra người gọi sở hữu đơn; nhận diện retry bằng key/fingerprint.
2. Xác minh PIN gắn với đơn và tổng tiền dự kiến.
3. Trong transaction tài chính: khóa đơn, khóa ví, kiểm tra lại trạng thái,
   quyền sở hữu, dữ liệu được xác nhận, tiền tệ và số dư; đọc idempotency bằng
   current read trước khi tạo thao tác mới.
4. Phân bổ BONUS theo opt-in và rule, CASH trả phần còn lại. Không đủ thì rollback.
5. Tạo PAYMENT/hold theo từng nguồn, chuyển khả dụng sang đang giữ, xác nhận kho
   và chuyển đơn `PENDING → PAID` cùng transaction.
6. Trả lại kết quả cũ khi retry cùng payload; cùng key khác payload trả conflict.

Đơn có `finalAmount = 0` được xử lý bằng nhánh không thu tiền; không tạo giao
dịch/hold amount 0 vì entity hiện cấm. Vẫn kiểm tra trạng thái, chủ sở hữu, xác
nhận kho và chống lặp bước chuyển đơn.

### Hủy/hoàn

- `OrderService.cancelLockedOrder()` và nhánh admin CANCELLED dùng chung một
  đường hoàn: chưa trả tiền thì chỉ hoàn kho/voucher; đã trả thì release các
  hold còn giữ và tạo REFUND trỏ tới PAYMENT gốc, cùng transaction hủy đơn.
- Khóa PAYMENT gốc rồi đọc các refund liên quan bằng current locking read;
  tổng PENDING/PROCESSING/SUCCESS không vượt hạn mức hoàn được duyệt.
- Hoàn đúng CASH/BONUS gốc; không tạo tiền CASH từ BONUS.
- Hoàn sau quyết toán cần dữ liệu bù trừ người bán/sàn riêng, không release lại
  hold đã capture. Chỉ mở chức năng này khi có mô hình hoàn hàng/nguồn bù tương ứng.

### Nạp/rút và ngân hàng

```text
Transaction DB A: tạo yêu cầu (+ giữ tiền nếu rút), lưu outbox → commit
Worker/gateway: gọi đối tác bằng merchant reference/idempotency key ổn định
Transaction DB B: áp dụng kết quả đã kiểm chứng → commit
```

- Không giữ transaction/khóa DB trong lúc gọi HTTP ngân hàng.
- Nạp chỉ cộng CASH khi có bằng chứng thành công; callback lặp không cộng lại.
- Rút thành công thì capture hold; thất bại chắc chắn thì release về CASH.
  Timeout/kết quả chưa rõ giữ `PROCESSING`, tra cứu bằng merchant reference.
- Outbox giúp tiếp tục sau crash giữa commit và gửi ngân hàng; worker có cơ chế
  claim/retry có giới hạn, đối tác phải hỗ trợ gửi lại hoặc tra cứu đủ an toàn.
- Callback/job dùng chung hàm áp dụng kết quả, khóa cùng thứ tự, kiểm tra lại
  trạng thái; thông báo đến muộn không được tự làm lùi trạng thái đã kết thúc.

### Quyết toán/cashback

Đơn đủ điều kiện → khóa các ví liên quan theo ID tăng dần → khóa các hold →
capture, giảm tiền đang giữ và ghi có người bán/phí theo phân bổ đã lưu, nguyên tử.
Không capture tiền rồi bỏ qua ghi nhận bên nhận. Cashback chỉ cấp một lần khi
đủ điều kiện và vào BONUS; chính sách thời điểm cấp cần tính tới trả hàng.
Thu hồi thưởng đã tiêu cần chính sách thu hồi/case riêng; không ép số dư âm.

### Tích hợp với code đơn hàng

- Chặn nhánh admin hiện có chỉ đổi trạng thái sang PAID mà không có bằng chứng
  thanh toán ví. Chuyển trạng thái PAID phải đi qua luồng thanh toán mới.
- Hoàn tiền đi qua cả đường khách/admin hủy và job hết hạn nếu có tiền đã giữ.
- Khi COMPLETED, chỉ quyết toán nếu thỏa điều kiện và đủ dữ liệu phân bổ.
- Wallet service có thể dùng OrderRepository/InventoryService; không gọi vòng
  lại OrderService khi OrderService đã gọi ví. Chia helper chuyển trạng thái nếu
  cần để giữ một đường xử lý kho và tránh lặp side effect.
- Chốt một thứ tự khóa cho đơn, ví, giao dịch gốc/con, hold và tài nguyên kho/
  voucher; rà soát cả checkout lẫn admin/job. Khởi tạo ví/PIN khóa user/cha riêng
  trước khi bước vào luồng tài chính; không thêm khóa user sau khi đã khóa ví.

## 6. Checklist dữ liệu và tình trạng triển khai

1. **Đích ngân hàng đã xác minh:** entity/liên kết lưu token đối tác, chủ sở hữu,
   thông tin che để hiển thị. Đã có LinkedBankAccount, chỉ tạo từ bằng chứng của adapter.
2. **Idempotency fingerprint:** WalletOperation lưu hash của payload nghiệp vụ đã
   chuẩn hóa, liên kết các transaction CASH/BONUS và kết quả để retry trả lại
   đúng thao tác. Không dùng mỗi key để chấp nhận request đã thay đổi.
3. **Outbox và dấu vết đối tác:** lưu merchant reference trước lần gửi đầu,
   payload đã loại secrets, retry metadata và dấu vết event/callback phù hợp
   giao thức. Provider ID có thể chưa biết nếu lần gửi đầu bị timeout.
4. **Người bán/phân bổ quyết toán:** Product có sellerId; OrderItem lưu snapshot
   người bán, tiền sau giảm giá và phí sàn. Dữ liệu cũ thiếu snapshot cần đối soát,
   không suy đoán người nhận tiền từ tổng đơn hay chủ sản phẩm hiện tại.
5. **Bút toán và audit:** wallet_transactions là vòng đời nghiệp vụ, chưa phải
   sổ kế toán kép. Đã thêm WalletJournal/WalletJournalEntry, audit khóa/mở khóa và
   đối soát số dư/khoản giữ. Không có API sửa balance trực tiếp.
6. **Hoàn hàng/cashback:** đã có hồ sơ hoàn toàn bộ đơn trong thời hạn, admin duyệt
   trước quyết toán. Cashback theo tỷ lệ snapshot (mặc định 0) sau thời hạn hoàn.
   Hoàn một phần/sau quyết toán và thu hồi thưởng đã tiêu vẫn cần nguồn bù/case riêng.

Reconciliation cần phân lô ổn định: thêm truy vấn keyset/cursor hoặc snapshot ID
trước khi xử lý; không tăng offset trên tập PENDING đang bị chính worker loại bớt.

## 7. API dự kiến

Các đường khởi tạo/đọc ví, lịch sử, khoản giữ và khóa/mở khóa đã triển khai.
Các đường PIN, nạp/rút và thanh toán đã có; callback vẫn chờ giao thức đối tác.

| API | Mục đích |
|---|---|
| `POST /api/wallet` | Khởi tạo ví idempotent cho tài khoản cũ; tài khoản mới tạo cùng đăng ký |
| `GET /api/wallet` | Xem ví của tôi |
| `GET /api/wallet/transactions` | Lịch sử có lọc/phân trang |
| `GET /api/wallet/transactions/{id}` | Chi tiết giao dịch thuộc ví của tôi |
| `GET /api/wallet/holds` | Khoản giữ thuộc ví của tôi |
| `POST /api/wallet/pin` | Thiết lập PIN |
| `PUT /api/wallet/pin` | Đổi PIN |
| `POST /api/wallet/top-ups` | Tạo yêu cầu nạp |
| `POST /api/wallet/withdrawals` | Yêu cầu rút |
| `POST /api/orders/{id}/pay` | Thanh toán đơn bằng ví |
| `POST /api/wallet/freeze` | Tự khóa ví |
| `POST /api/admin/wallets/{id}/freeze` / `unfreeze` | Thao tác quản trị có lý do và audit |
| `POST /api/wallet/bank-events/{provider}` | Callback theo adapter, xác thực bằng cơ chế đối tác |

Khách không có API tự đánh dấu nạp/rút thành công, tự refund/capture/cashback
hay mở khóa ví bằng mỗi một cờ. Việc xác minh tài khoản ngân hàng có API riêng
sử dụng bằng chứng từ adapter sau khi chọn được đối tác.

## 8. Thứ tự triển khai và điều kiện nghiệm thu

1. **Nền tảng:** chốt quy tắc tiền, migration, fingerprint, mã lỗi; DTO/mapper,
   WalletService đọc/khởi tạo, phân quyền qua UserAccountsApi, PIN và audit khóa.
2. **Nạp/rút:** linked bank account, BankGateway, outbox, callback đã xác minh và
   các transaction cập nhật tiền; gateway test phục vụ kiểm thử trước adapter thật.
3. **Thanh toán/hủy:** PAYMENT/hold, checkout PENDING, PAID đúng luồng, hủy hoàn
   tiền nguyên tử với kho/voucher, cả luồng admin và job.
4. **Quyết toán:** seller snapshot, phân bổ phí/BONUS, bút toán và kiểm tra đối
   soát; hoàn hàng sau quyết toán chỉ mở khi có nguồn bù và case được duyệt.
5. **Mở rộng/vận hành:** cashback/thu hồi, reconciliation cursor, cảnh báo,
   khôi phục PIN và sinh trắc học theo cơ chế xác thực được chọn.

Kiểm thử nghiệm thu tập trung vào: sai chủ sở hữu; PIN sai vẫn tăng bộ đếm khi
thao tác bị từ chối; hai lần chi cùng số dư; cùng key cùng/khác payload; hai khoản
hoàn đồng thời; tách đúng CASH/BONUS; thanh toán đua với hủy/job; rollback kho/
voucher/tiền; đơn 0 đồng; callback trùng/sai tiền/sai ví/đến muộn; ngân hàng timeout
nhưng đã chuyển tiền; crash trước/sau gửi outbox; khóa ví đua với ghi nợ; và tổng
tiền giữ khớp hold/bút toán. Dùng database kiểm thử riêng như các test hiện có.
