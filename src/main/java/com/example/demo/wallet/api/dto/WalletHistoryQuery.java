package com.example.demo.wallet.api.dto;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;

import com.example.demo.shared.web.PageQuery;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletHistoryQuery extends PageQuery {
    private TransactionType type;
    private TransactionStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;

    @AssertTrue(message = "Thời điểm bắt đầu phải trước thời điểm kết thúc")
    @JsonIgnore
    public boolean isTimeRangeValid() {
        return from == null || to == null || from.isBefore(to);
    }

    @Override
    public Pageable toPageable() {
        // Repository owns the stable createdAt DESC, id DESC ordering.
        return PageRequest.of(getPage() - 1, getSize());
    }
}
