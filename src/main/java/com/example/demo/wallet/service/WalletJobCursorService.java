package com.example.demo.wallet.service;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.wallet.repository.WalletJobCursorRepository;
import lombok.RequiredArgsConstructor;
/** Claim IDs durably before processing. A crash revisits an unfinished batch on the next wrap. */
@Service @RequiredArgsConstructor
public class WalletJobCursorService {
    private final WalletJobCursorRepository cursors;
    @Transactional
    public List<Long> claim(String name, BiFunction<Long,Pageable,List<Long>> query) {
        cursors.initialize(name);
        var cursor=cursors.lock(name).orElseThrow();
        var ids=query.apply(cursor.getLastId(),PageRequest.of(0,100));
        cursor.setLastId(ids.isEmpty()?0:ids.getLast());
        return ids;
    }
}
