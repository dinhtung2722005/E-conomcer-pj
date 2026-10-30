package com.example.demo.wallet.dto;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.demo.user.dto_request.PageQuery;
import com.example.demo.wallet.entity.WalletHoldStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletHoldQuery extends PageQuery {
    @NotNull
    @Size(min = 1, message = "Phải chọn ít nhất một trạng thái khoản giữ")
    private Set<@NotNull WalletHoldStatus> statuses = EnumSet.allOf(WalletHoldStatus.class);

    @Override
    public Pageable toPageable() {
        return PageRequest.of(getPage() - 1, getSize());
    }
}
