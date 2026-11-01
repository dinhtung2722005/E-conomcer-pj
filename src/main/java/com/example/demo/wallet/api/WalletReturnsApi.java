package com.example.demo.wallet.api;
import java.math.BigDecimal;
import java.util.List;
import com.example.demo.ecommerce.api.OrderSnapshot;
import com.example.demo.user.api.UserAccount;
import com.example.demo.wallet.api.dto.*;
public interface WalletReturnsApi {
    record ReturnedLine(Long orderItemId,int quantity){}
    record Decision(RefundCaseResponse response,boolean newlyApproved,boolean fullyReturned,BigDecimal amount,List<ReturnedLine> lines){
        public Decision { lines=List.copyOf(lines); }
    }
    RefundCaseResponse request(OrderSnapshot order,RefundCaseRequest request,boolean adminException);
    RefundCaseResponse get(Long orderId);
    Decision decide(OrderSnapshot order,UserAccount actor,RefundDecisionRequest request);
}
