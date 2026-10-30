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
