package com.example.demo.wallet.api;
import com.example.demo.wallet.api.dto.*;
public interface ReturnUseCase {
    RefundCaseResponse requestRefund(Long orderId,RefundCaseRequest request);
    RefundCaseResponse requestAdminRefund(Long orderId,RefundCaseRequest request);
    RefundCaseResponse getMyRefund(Long orderId);
    RefundCaseResponse decide(Long orderId,RefundDecisionRequest request);
}
