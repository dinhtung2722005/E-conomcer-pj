# Quyết toán và vận hành ví

## Luồng đã triển khai

Ngân hàng → CASH khả dụng → PAYMENT/hold → hết hạn hoàn → ví CASH người bán.
Mua hàng hoàn toàn bằng ví; `useBonus=true` dùng BONUS trước rồi CASH. Hủy trước
giao hoặc hoàn được duyệt trả về đúng nguồn ban đầu trong cùng transaction với
kho/voucher. Ví LOCKED chặn chi/rút mới, vẫn nhận nạp, hoàn, thưởng và quyết toán.

Các service:

| Service | Trách nhiệm |
|---|---|
| WalletService | Khởi tạo, truy vấn, lịch sử, giữ tiền, khóa/mở có audit |
| WalletPinService | BCrypt, thiết lập/đổi, giới hạn sai, cấp xác thực nội bộ ngắn hạn |
| WalletOperationService | Khóa yêu cầu và fingerprint cho retry |
| WalletFundingService / WalletBankWorker | Xác minh tài khoản qua gateway, nạp/rút và outbox |
| WalletPaymentService | Khóa đơn/ví, giữ CASH/BONUS, xác nhận kho, PAID |
| WalletOrderFundsService | Hoàn đúng nguồn, liên kết PAYMENT gốc, chống hoàn quá tiền |
| WalletRefundService | Hồ sơ hoàn toàn bộ đơn, quyền chủ đơn và duyệt ADMIN |
| WalletSettlementPolicy / WalletSettlementService | Snapshot, chia tiền người bán/phí, capture và cashback |
| WalletLedgerService | Sổ đối ứng cân bằng, ghi cùng transaction thay đổi số dư |
| WalletReconciliationService | So số dư với journal và pending với hold, không tự sửa tiền |

## Chính sách hiện tại

| Property | Mặc định được chọn |
|---|---|
| `wallet.settlement.fee-percent` | 0 |
| `wallet.cashback.percent` | 0 (tắt) |
| `wallet.settlement.return-window` | P7D |
| `wallet.settlement.worker-enabled` | true |
| `wallet.settlement.poll-delay-ms` | 60000 |
| `wallet.reconciliation.worker-enabled` | true |
| `wallet.reconciliation.poll-delay-ms` | 3600000 |

Phí/cashback chấp nhận 0–100, tối đa 4 chữ số thập phân. Policy được lưu trên đơn
tại checkout, thay cấu hình không sửa đơn đã đặt. Cashback tính từ finalAmount,
làm tròn xuống 4 chữ số, chỉ cộng BONUS một lần khi quyết toán; nguồn đối ứng là
`platform:PROMOTIONS`. Cần cấp ngân sách thực cho chương trình trước khi bật.

## Người bán và phân bổ

Admin có thể gán `sellerId` trong ProductRequest; phải là SELLER đang hoạt động.
Checkout lưu sellerId trên từng OrderItem. FinalAmount sau voucher được phân bổ
theo tỷ trọng giá dòng, làm tròn xuống 4 chữ số; dòng cuối nhận phần dư, tổng đúng
bằng số tiền đơn. Phí được snapshot trên từng dòng. Khi quyết toán, người bán
nhận CASH = tiền được phân bổ trừ phí; phí ghi tài khoản `platform:FEES`.
BONUS khách đã sử dụng cũng tham gia tiền trả người bán, có bút toán đối ứng.

Product/OrderItem cũ có thể chưa có sellerId hoặc phân bổ: quyết toán dừng và log
để xử lý dữ liệu, không lấy chủ sản phẩm hiện tại làm chủ đơn cũ. Người bán cần có
ví; đăng ký mới tự tạo, tài khoản cũ dùng POST /api/wallet trước khi nhận quyết toán.

## Nhận hàng và hoàn hàng

1. Sau PAID, admin chuyển SHIPPED theo API đơn hiện có.
2. Chủ đơn gọi POST `/api/orders/{id}/confirm-receipt` (hoặc admin chuyển COMPLETED).
   Hệ thống lưu completedAt và settlementAvailableAt = completedAt + 7 ngày theo
   snapshot. Đơn COMPLETED vẫn giữ tiền trong thời hạn này.
3. Chủ đơn có thể POST `/api/orders/{id}/refund` với `{ "reason": "..." }` trước
   settlementAvailableAt, và GET cùng đường dẫn để xem hồ sơ. Mỗi đơn một hồ sơ;
   retry cùng lý do trả hồ sơ hiện có, không tạo thêm.
4. ADMIN POST `/api/admin/orders/{id}/refund/decision` với
   `{ "approved": true, "reason": "Đã kiểm tra hàng trả" }` để duyệt sau khi kiểm
   tra hàng. Duyệt hoàn toàn bộ CASH/BONUS, trả kho/voucher, chuyển REFUNDED. Từ
   chối dùng approved=false. Lưu người duyệt, lý do và thời điểm; không ghi đè
   quyết định đã chốt bằng một request khác.
5. Worker chỉ quyết toán COMPLETED đã đủ hạn và không có hồ sơ REQUESTED/APPROVED.
   REQUESTED giữ tiền kể cả đã quá hạn để chờ xử lý; REJECTED cho phép quyết toán.

Chưa hỗ trợ hoàn một phần hoặc hoàn sau giải ngân, cũng chưa có API thu hồi BONUS
đã tiêu. Những luồng này cần nguồn bù và chính sách công nợ riêng; không release
lại hold đã capture hay ép số dư âm. Sinh trắc học/khôi phục PIN chờ cơ chế xác
minh bổ sung; không có cờ từ frontend để bỏ qua PIN.

## Ghi sổ và đối soát

Mỗi journal gồm các số có dấu cộng lại bằng 0 trong một loại tiền, eventKey duy
nhất; journal/entry được đánh dấu Immutable. Các tài khoản ví tách CASH/BONUS,
AVAILABLE/HELD. Có đối ứng BANK_CLEARING, OPENING, FEES và PROMOTIONS cho sàn.
Trước biến động đầu tiên, ví có sẵn số dư được ghi snapshot OPENING một lần dưới
khóa ví. Đây là mốc chuyển hệ thống, không thay thế việc xác minh nguồn tiền cũ.

Giải ngân khóa Order trước, rồi toàn bộ ví theo ID tăng dần, PAYMENT gốc và hold.
Capture, cộng người bán/phí, cashback, journal và settledAt cùng commit; bất kỳ
lỗi nào đều rollback toàn bộ. Không gọi HTTP ngân hàng khi đang giữ khóa DB.

ADMIN GET `/api/admin/wallets/{id}/reconciliation` trả chênh lệch giữa 4 số dư và
sổ, cùng chênh pending so với tổng hold còn lại. Ví chưa có mốc OPENING trả
ledgerInitialized=false, ledgerConsistent=null. Job quét theo cursor ID, log lỗi
khi có chênh; báo cáo không tự điều chỉnh balance. Cần nối log với hệ thống giám
sát của môi trường triển khai. BankTransfer.lastError/attempts cho biết giao dịch
ngân hàng cần rà soát; timeout không tự giải phóng tiền rút.

## Database và kiểm thử

Chưa chạy migration trên database ứng dụng. Dự án vẫn dùng cấu hình Hibernate
hiện có; trước khi dùng database có dữ liệu, chuẩn bị migration/backfill theo
[mô hình ví](wallet-model.md), gồm các bảng operation/outbox/journal/refund,
ledger_opened và các snapshot đơn hàng. Không tự suy đoán trạng thái tiền cũ.

Kiểm thử dùng MySQL tách biệt và gateway chỉ trong src/test. Bao phủ PIN sai vẫn
commit, yêu cầu lặp/đổi payload, hai lần chi cùng tiền, timeout, sai dữ liệu ngân
hàng, rollback kho/journal, chia nhiều người bán, hoàn trước giải ngân và đối soát
phát hiện sai lệch. Không có tiền thật được chuyển trong kiểm thử.
