# Ranh giới module và tầng điều phối

Ứng dụng vẫn chạy trong một Spring Boot process và dùng chung database. Giao
tiếp nội bộ là lời gọi Java qua Spring bean, đồng bộ. Các URL và cấu trúc JSON
hiện có được giữ nguyên; package Java của DTO/enum đã chuyển sang API công khai.

## Quyền sở hữu và phụ thuộc

| Phần | Sở hữu | Được phụ thuộc |
|---|---|---|
| user | Tài khoản, xác thực, mật khẩu, trạng thái, thu hồi phiên | shared |
| ecommerce | Đơn, tồn kho, voucher, phân bổ và snapshot chính sách | user.api, shared |
| wallet | Số dư, hold, journal, giao dịch, hoàn tiền, công nợ | user.api, ecommerce.api, shared |
| application | Điều phối nghiệp vụ giữa các module và HTTP correlation | API của ba module, shared |
| observability | Encoder log JSON | shared |
| shared | Phân trang/response chung, lỗi, logging context và redaction | Thư viện nền tảng |

API công khai nằm trong `user.api`, `ecommerce.api`, `wallet.api`; DTO ở
`*.api.dto`. Repository, service triển khai và entity là nội bộ. Module khác
không được import hay truy vấn các entity/bảng nội bộ đó. `wallet` được đọc
snapshot và gọi các thao tác vận hành đơn qua `ecommerce.api`, không sửa Order
hoặc gọi OrderRepository. Snapshot là immutable, danh sách item được sao chép;
metadata user không chứa mật khẩu/hash. Identity và role luôn do user cung cấp.

Các interface use case được khai báo tại API của module và triển khai ở
`application`. Controller/job inject interface nên không tạo chiều phụ thuộc
từ module nghiệp vụ về implementation của application.

## Các luồng chính

| Điều phối | Trình tự |
|---|---|
| RegistrationApplicationService | Tạo account qua UserRegistrationApi → khởi tạo ví qua WalletAccountsApi |
| OrderPaymentApplicationService | Xác thực PIN → transaction tiền: khóa user → khóa đơn → giữ tiền → xác nhận kho và PAID |
| OrderWorkflowApplicationService | Khóa/kiểm tra đơn → hoàn CASH/BONUS qua ví → trả kho/voucher và CANCELLED |
| ReturnApplicationService | Khóa actor/đơn → ví kiểm tra hồ sơ, hoàn tiền và ghi journal → ecommerce trả đúng số lượng, cập nhật số tiền hoàn/trạng thái |
| SettlementApplicationService | Khóa đơn → ví kiểm tra hồ sơ hoàn, capture và trả seller/phí/công nợ → ecommerce chốt settledAmount/settledAt |

RegistrationUseCase được dùng cho cả đăng ký và admin tạo tài khoản. Các phương
thức tạo account thô không còn là API cho controller. Job hủy đơn quá hạn cũng
gọi OrderWorkflowUseCase; mỗi đơn có transaction riêng.

Ví không nhận Order/OrderItem/User JPA từ module khác. Các liên kết được giữ
bằng userId, orderId, orderItemId và actorId, cùng dữ liệu snapshot đã khóa do
ecommerce tạo. WalletReturnService chỉ xử lý phần tiền và hồ sơ; số lượng còn
được hoàn lấy từ lịch sử hồ sơ duyệt dưới cùng khóa đơn. Ecommerce xử lý tồn
kho/voucher và cập nhật Order. Retry quyết định đã chốt không áp dụng trả kho lần nữa.

## Transaction và bảo mật

Các bước tiền–đơn–kho của cùng use case cùng commit/rollback. Facade tài chính và
API ghi đơn yêu cầu transaction đang tồn tại bằng MANDATORY. PIN vẫn xác thực
trong transaction ngắn REQUIRES_NEW trước transaction tiền; sai PIN và lịch sử
bảo mật được commit dù nghiệp vụ phía sau bị từ chối.

PaymentPermit là handle nội bộ do ví cấp, gắn user/order/useBonus, phiên đăng
nhập, phiên bản bảo mật và thời hạn. Module khác không tạo được implementation
hợp lệ. `lockPayer` kiểm tra lại tài khoản/phiên và ghi nhận bằng synchronization
của transaction hiện tại. `reserve` chỉ nhận permit đã khóa trong chính
transaction đó, đúng đơn/chủ đơn và phiên bản PIN; không dùng chứng nhận khóa
của transaction đã kết thúc hay REQUIRES_NEW khác. Permit không phải DTO của client.

Luồng khóa giữ nguyên user → order → các wallet theo ID tăng dần → security/
transactions/holds → inventory. Các luồng hủy/quyết toán không cần xác thực PIN
giữ order trước wallet như trước. Không thêm khóa user sau wallet vào các luồng đó.
Ngân hàng/SMTP không được gọi khi transaction tiền đang giữ khóa. Nguồn tiền,
idempotency, phí 0%, cashback tắt và cửa sổ hoàn 7 ngày không đổi.

Thông báo và ngân hàng tiếp tục dùng worker/cursor/outbox hiện có. Không thay
thế cơ chế bền vững này bằng event listener chỉ tồn tại trong bộ nhớ.

## Database

Tên bảng/cột và dữ liệu không đổi khi chuyển liên kết JPA chéo module sang ID.
Database đã chạy bản cũ: giữ nguyên các khóa ngoại hiện có, không drop để refactor.
Chưa chạy SQL hay thay schema trên database ứng dụng trong thay đổi này.

Database mới tạo từ entity cần chạy
`src/main/resources/db/manual/module-boundary-foreign-keys.sql` sau khi tạo bảng,
hoặc đưa các ALTER tương ứng vào migration quản lý schema. Script dành cho
schema mới; database cũ cần đối chiếu information_schema và chỉ thêm khóa còn
thiếu. Ràng buộc user, seller, actor, hold/order và refund/order-item vẫn được
bảo vệ ở database. Không dựa riêng vào kiểm tra Java để thay khóa ngoại.
Hold vẫn có Bean Validation kiểm tra chủ đơn qua OrderOwnershipApi chỉ trả ID;
không giữ entity Order trong ví. API lookup dùng EntityManager.find phía ecommerce
để đọc Order đã có trong persistence context, tránh JPQL auto-flush đệ quy khi
Hibernate đang kiểm tra hold trước INSERT/UPDATE. Kiểm tra này bổ sung cho quyền
chủ đơn/permit và khóa dữ liệu ở tầng điều phối, không thay thế chúng.
DomainValidationConfiguration truyền ValidatorFactory của Spring cho Hibernate,
để callback JPA dùng cùng validator được inject API như MVC/method validation.
Trong production, migration chạy bằng tài khoản riêng và runtime dùng ddl-auto=validate.
Đã có Flyway V1–V3, profile prod kiểm tra cấu hình và bảng quota bền vững;
xem [triển khai và nâng cấp](wallet-hardening.md). Khi dùng Flyway tạo database mới,
V1 đã có các khóa ngoại nên không chạy thêm script ALTER của schema Hibernate.

IsolatedMySqlDatabase trong test tạo database tách biệt và áp dụng các khóa ngoại
trước khi test chạy, sau đó xóa riêng database test. Các kiểm thử orphan/foreign
key không bị bỏ qua khi tháo quan hệ JPA.

## Kiểm thử kiến trúc

ModuleArchitectureTests dùng Spring ASM có sẵn để kiểm tra class đã biên dịch,
gồm field/method/generic signature, annotation, lambda và lời gọi trong thân hàm.
Kiểm tra thêm tên entity/bảng trong JPQL/native @Query để phát hiện truy cập
chéo không xuất hiện dưới dạng Java import. Build thất bại khi có cạnh phụ thuộc
không được phép, API lộ implementation/entity hoặc xuất hiện vòng module.

Các kiểm thử nghiệp vụ gọi interface use case thay cho service nội bộ, tiếp tục
kiểm tra chi đồng thời, retry, quyền sở hữu, rollback kho/journal, hoàn một phần,
quyết toán, công nợ và khôi phục PIN. Có thêm kiểm tra permit giả, permit sai đơn,
thiếu khóa user/cùng transaction và đăng ký thất bại không để lại account hoặc ví.

Chạy riêng quy tắc kiến trúc bằng `./mvnw -Dtest=ModuleArchitectureTests test`;
chạy toàn bộ bằng `./mvnw clean test`. Kiểm thử tích hợp cần MySQL theo cấu hình
test và quyền tạo/xóa database test. Nếu IDE cũng đang biên dịch, dùng thư mục
build riêng cho Maven để tránh trộn class cũ và class mới.
