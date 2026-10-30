# Mô hình ví nội bộ

Ngân hàng chỉ tham gia khi nạp/rút. Đơn hàng thanh toán hoàn toàn bằng ví; thiếu
số dư thì phải nạp thêm, không thanh toán kết hợp với thẻ/ngân hàng tại checkout.

## Entity

| Entity | Vai trò |
|---|---|
| `Wallet` | Một ví cho mỗi user, một loại tiền tệ, bốn ngăn số dư và khóa lạc quan |
| `WalletHold` | Khoản tiền giữ của một đơn hoặc lệnh rút; theo dõi số đã quyết toán, đã giải phóng và còn giữ |
| `WalletTransaction` | Từng nghiệp vụ nạp, rút, thanh toán, hoàn tiền, cashback, thu hồi cashback hoặc ghi có doanh thu người bán |
| `WalletSecurity` | Hash PIN, số lần nhập sai, thời gian khóa PIN; dùng chung khóa chính với ví |
| `WalletStatusChange` | Audit thay đổi trạng thái ví: người thực hiện, vai trò lúc thao tác, trạng thái cũ/mới, lý do và thời gian |

`balance` là tiền thật **khả dụng**, `pendingBalance` là tiền thật **đang giữ**.
`bonusBalance` và `pendingBonusBalance` là hai ngăn tương ứng của tiền thưởng.
Bốn số dư tách biệt, không cộng tiền đang giữ vào tiền khả dụng khi kiểm tra thanh toán.
`currency` mặc định `VND`; precision/scale giữ `19,4` như các entity tiền hiện có.
Quy tắc làm tròn và số chữ số thập phân được phép theo tiền tệ thuộc service.

`userId` giữ API hiện có; association `user` chỉ đọc để tạo foreign key tới `users`.
Không chuyển quyền sở hữu ví hoặc thay tiền tệ sau khi tạo.

## Luồng tiền

1. **Nạp:** tạo `DEPOSIT/PENDING` với mã yêu cầu ổn định. Chỉ khi backend xác minh
   kết quả đối tác mới chuyển `SUCCESS` và cộng `balance`, trong cùng transaction DB.
2. **Thanh toán:** chuyển tiền từ số dư khả dụng sang đang giữ và tạo
   `ORDER_PAYMENT` hold cùng `PAYMENT`. Một đơn dùng cả tiền thật và tiền thưởng
   có hai hold/transaction, mỗi loại `CASH`/`BONUS` một bản ghi. Đây là phân bổ
   nội bộ ví, không phải split payment với ngân hàng.
3. **Quyết toán:** `capture()` khoản giữ, giảm số đang giữ, ghi có số dư tiền thật
   người bán bằng `SETTLEMENT` và ghi nhận phí sàn. Chỉ thực hiện khi đơn đủ điều
   kiện và không có tranh chấp đang chặn quyết toán.
4. **Hủy trước quyết toán:** `release()` khoản giữ, trả về đúng ngăn `CASH`/`BONUS`,
   ghi `REFUND` liên kết với `PAYMENT` gốc. Đơn chưa thanh toán không được cộng tiền.
5. **Hoàn sau quyết toán:** ghi nghiệp vụ hoàn mới liên kết thanh toán gốc; service
   phải xử lý nguồn bù tiền từ người bán/sàn, không release lại hold đã capture.
6. **Rút:** tạo `WITHDRAWAL/CASH` hold và `WITHDRAW` với token tài khoản ngân hàng
   đã xác minh. Thành công thì capture; thất bại chắc chắn thì release. Timeout
   vẫn là `PROCESSING` cho tới khi tra cứu/đối soát xác định kết quả.
7. **Cashback:** cộng `bonusBalance` bằng `CASHBACK/BONUS` khi đủ điều kiện. Thu hồi
   dùng `CASHBACK_REVERSAL` liên kết cashback gốc; không biến tiền thưởng thành tiền rút được.

Hold có các trạng thái `ACTIVE`, `PARTIALLY_PROCESSED`, `CAPTURED`, `RELEASED`,
`CLOSED` (một phần capture, phần còn lại release). Luôn có:

```text
remainingAmount = amount - capturedAmount - releasedAmount >= 0
```

`capture()`/`release()` chỉ cập nhật hold, không tự cập nhật ví hay gọi ngân hàng.
Service phải cập nhật ví, hold, giao dịch và đơn hàng nguyên tử; bắt xung đột
`@Version` hoặc dùng khóa dòng. Giải phóng hold là trả tiền khả dụng, không phải
chuyển tiền cho người bán.

## Ràng buộc và trách nhiệm của service

- Unique `(wallet_id, idempotency_key)` ngăn một nghiệp vụ được lưu hai lần.
  Khóa giữ nguyên khi retry; `CASH`/`BONUS` có khóa dẫn xuất riêng. Khi gặp khóa
  đã tồn tại, service phải so sánh payload trước khi trả kết quả cũ.
- Unique `(provider, provider_transaction_id)` ngăn ghi nhận lại cùng nghiệp vụ
  đối tác, kể cả đổi ví. Lưu ID của chính nghiệp vụ (refund ID cho hoàn tiền),
  chuẩn hóa namespace đối tác nếu có nhiều tài khoản merchant/môi trường.
- `referenceId` là mã nghiệp vụ gốc, không unique: thanh toán, hoàn một phần và
  quyết toán có thể cùng tham chiếu một đơn. Lệnh rút dùng một reference ổn định.
- DB có CHECK cho số tiền dương, số dư không âm, phân bổ/trạng thái hold,
  nguồn tiền và các liên kết bắt buộc; Bean Validation bổ sung kiểm tra quan hệ.
- Refund phải trỏ tới `PAYMENT/SUCCESS` cùng ví, reference và ngăn tiền. Service
  vẫn phải khóa thanh toán gốc và kiểm tra **tổng** các khoản hoàn thành công/đang
  chờ không vượt khoản được phép hoàn. Entity không kiểm tra tổng nhiều dòng.
- Kiểm tra số giữ còn lại khớp `pendingBalance`/`pendingBonusBalance`; kiểm soát
  tổng nguồn tiền giữ bằng giá trị đơn; không dựa riêng vào CHECK từng dòng.
- `LOCKED` chặn nghiệp vụ chi mới, vẫn cho nhận nạp/hoàn. Service phải kiểm tra
  trạng thái ở mọi đường ghi nợ, và có chính sách riêng cho lệnh đã gửi ngân hàng.
- `WalletSecurity.pinHash` chỉ lưu hash do password encoder phù hợp sinh ra;
  bị loại khỏi JSON. Service thực thi xác nhận PIN, giới hạn thử, reset PIN,
  xác thực bổ sung và kiểm chứng tài khoản ngân hàng thuộc người yêu cầu.
  Entity không lưu PIN thô hoặc dữ liệu vân tay/khuôn mặt.
- `wallet_transactions` theo dõi vòng đời nghiệp vụ; không tự bảo đảm sổ kế toán
  kép của toàn sàn. Các bút toán đối ứng ngân hàng/người bán/phí và cơ chế đối soát
  cần được triển khai cùng nghiệp vụ ví. Không sửa/xóa giao dịch thành công để
  điều chỉnh tiền; tạo nghiệp vụ bù có liên kết và dấu vết quản trị.

## Repository

- `WalletRepository`: tra cứu ví theo chủ sở hữu, khóa một ví hoặc nhiều ví theo
  thứ tự ID tăng dần khi thay đổi tiền/quyết toán.
- `WalletTransactionRepository`: tìm theo khóa chống trùng/mã đối tác, lịch sử
  theo ví có phân trang/lọc thời gian `[fromInclusive, toExclusive)`, tra cứu theo
  reference và quét có giới hạn các giao dịch cần đối soát.
- `WalletHoldRepository`: tìm khoản giữ theo ví, đơn, loại và nguồn tiền; khóa
  các khoản giữ của một đơn theo thứ tự ID; phân trang theo trạng thái.
- `WalletSecurityRepository`: dùng `findById(walletId)` để đọc; khóa bản ghi bảo
  vệ PIN qua `findByWalletIdForUpdate` trước khi cập nhật số lần sai/trạng thái khóa.

Mọi phương thức `ForUpdate` phải chạy trong transaction đọc/ghi của service.
Khóa cần được giữ cho tới khi cập nhật xong số dư, hold và giao dịch; không mở
transaction riêng chỉ bao quanh câu SELECT. Truy vấn giới hạn theo ví không tự
xác thực người gọi: service phải lấy ví thuộc user đăng nhập trước khi sử dụng.
Truy vấn đối tác và quét đối soát dành cho backend, không mở trực tiếp cho khách.

Khi hoàn/thu hồi thưởng, khóa giao dịch gốc bằng phương thức có `walletId`, rồi
gọi `findRelatedTransactionsForUpdate` và tính tổng các trạng thái `PENDING`,
`PROCESSING`, `SUCCESS`. Khóa giao dịch gốc ngăn hai yêu cầu đồng thời cùng tạo
thêm refund; chỉ khóa các dòng refund hiện có không bảo vệ được lúc chưa có dòng.
Truy vấn khóa các dòng con là current read, tránh dựa vào aggregate/snapshot cũ.
Các luồng phải thống nhất thứ tự khóa ở service để tránh deadlock.

Khóa một dòng chưa tồn tại không thay thế unique constraint: khởi tạo ví/PIN và
xử lý idempotency vẫn cần khóa bản ghi cha phù hợp, unique constraint và xử lý
xung đột. Các repository kế thừa `JpaRepository` để theo convention của dự án;
service không dùng delete để xóa lịch sử tài chính hoặc hold đã phát sinh tiền.

## WalletService đã triển khai

- `initializeWallet(userId)` là hook nội bộ cho ứng dụng/đăng ký, không bind userId
  từ HTTP khách. Khóa user trước rồi đọc/khởi tạo ví bằng current read để tránh
  tạo trùng khi hai yêu cầu đồng thời. Chưa nối hook này vào AuthService.
- `initializeMyWallet()`, `getMyWallet()`, `getHistory(query)`, `getTransaction(id)`
  và `getHolds(query)` lấy chủ sở hữu từ CurrentUserService. GET không tự tạo ví.
- `freezeMyWallet(request)` chỉ khóa ví hiện tại. `freezeWallet(id, request)` và
  `unfreezeWallet(id, request)` yêu cầu ADMIN ở tầng method và kiểm tra lại vai
  trò trong DB. User không tự mở khóa. User bị vô hiệu hóa không thao tác được.
- Mỗi thay đổi trạng thái thực tế có một `WalletStatusChange` trong cùng transaction;
  audit lỗi thì trạng thái rollback. Retry trạng thái giống hiện tại không thêm audit.
  Các thao tác này không thay đổi bốn số dư hoặc trạng thái hold/giao dịch.
- Các luồng ghi khóa user thực hiện trước, rồi ví; reads dùng transaction read-only.
  Query DTO có validation cả ở service: page >= 1, size <= 100, from < to, danh
  sách trạng thái hold không rỗng. Thứ tự lịch sử luôn createdAt DESC, id DESC.
- Response DTO không chứa entity, PIN/hash, idempotency key hoặc token ngân hàng.
  Lỗi raw từ đối tác không trả cho khách; giao dịch FAILED dùng thông báo chung.
- Đã có controller/API quản lý ví tại `/api/wallet` và `/api/admin/wallets`;
  xem [API ví](wallet-api.md). Chưa có WalletSecurityService hoặc nghiệp vụ ghi tiền. Mở
  khóa quản trị trong bước này dựa trên quyền ADMIN và lý do có audit; khi thêm
  xác minh nâng cao cần đặt trước luồng này, không mở unfreeze cho khách.

## DTO đã có

- Đầu vào: TopUpRequest, WithdrawRequest, PayOrderRequest, SetupPinRequest,
  ChangePinRequest, WalletHistoryQuery, WalletHoldQuery, WalletFreezeRequest.
- Đầu ra: WalletResponse, WalletTransactionResponse, WalletHoldResponse,
  TopUpResponse, WithdrawResponse, PaymentResponse, RefundResponse,
  WalletSecurityResponse; hai DTO con TopUpInstructionsResponse và
  BankAccountSummaryResponse dùng cho hướng dẫn nạp và đích rút đã che.
- Số tiền nạp/rút dương, tối đa 15 chữ số nguyên/4 chữ số thập phân như schema;
  linkedBankAccountId dương. Kiểm tra tiền tệ/hạn mức/chủ sở hữu và trạng thái
  xác minh tài khoản ngân hàng vẫn thuộc service.
- PIN là chuỗi đúng 6 chữ số ASCII, giữ được số 0 đầu; thiết lập/đổi phải khớp
  confirmPin. PIN/mật khẩu là WRITE_ONLY khi bind JSON, DTO không có toString
  chứa dữ liệu này. Không log body request/giá trị lỗi validation của secrets.
- WalletMapper có thêm toSecurityResponse; các response nạp/rút/thanh toán/
  hoàn tiền sẽ được dựng trong service tương ứng khi triển khai nghiệp vụ.

## Database đang có dữ liệu

Thay đổi này sửa entity, chưa chạy migration trên database ứng dụng. Không dựa
vào `ddl-auto=update` để backfill hoặc bổ sung đầy đủ constraint cho bảng cũ.
Trước khi triển khai lên database hiện có cần migration được kiểm tra trên bản sao:

1. Đối soát/giải quyết số dư âm, ví mồ côi và xác định ý nghĩa số dư chờ cũ.
2. Thêm `bonus_balance`, `pending_bonus_balance` mặc định `0`, `currency` mặc định
   `VND`; mở rộng các enum và thêm các cột giao dịch mới.
3. Backfill khóa idempotency duy nhất cho dữ liệu lịch sử (ví dụ `legacy:<id>`),
   rồi mới đặt NOT NULL/unique. Không suy đoán provider ID từ reference cũ.
4. Liên kết các khoản refund cũ với payment gốc; dựng lại hold của các payment/
   withdrawal chưa quyết toán từ dữ liệu thật, không tự tạo số dư giả để qua CHECK.
5. Tạo bảng mới, index, foreign key và CHECK; kiểm tra đối soát trước khi mở ghi.
   `WalletService` bổ sung bảng `wallet_status_changes`; cần tạo bảng audit này
   trong migration trước khi dùng thao tác khóa/mở khóa trên database cũ.

## Kiểm thử

`WalletEntityIntegrationTests` dùng database MySQL riêng qua
`IsolatedMySqlDatabase`, không sửa schema/dữ liệu ứng dụng. Kiểm tra ràng buộc
DB, khóa chống trùng, xung đột phiên bản, phân bổ hold, nguồn tiền hoàn và bảo vệ
hash PIN trong JSON. `WalletRepositoryIntegrationTests` kiểm tra phân quyền theo
phạm vi truy vấn, phân trang/lọc, truy vấn khóa và hai yêu cầu chi tiền đồng thời.
`WalletServiceIntegrationTests` kiểm tra khởi tạo đồng thời, quyền sở hữu, role
ADMIN, validation DTO, audit idempotent và rollback khi ghi audit thất bại.
`WalletDtoTests` kiểm tra ranh giới số tiền/PIN và việc không serialize secrets,
chạy độc lập với database.
`WalletControllerIntegrationTests` gọi HTTP/JWT thật, kiểm tra xác thực/phân quyền,
scope chủ sở hữu, bind/validate query/body, HTTP status và audit qua API.
Controller quản lý ví đã có; tích hợp checkout chưa được thêm.
