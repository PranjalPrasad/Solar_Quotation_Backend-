package com.solar.Quotation.util;

import com.solar.Quotation.entity.CodeSequence;
import com.solar.Quotation.repository.CodeSequenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Human codes. The counter row is locked (SELECT ... FOR UPDATE) so two requests never get the same number.
 * Counters are per partner + prefix, so partners never contend on one row.
 */
@Component
public class CodeGenerator {

    private final CodeSequenceRepository repository;

    public CodeGenerator(CodeSequenceRepository repository) {
        this.repository = repository;
    }

    /** CUST-1001, CUST-1002 ... (starts at 1001). */
    @Transactional
    public String next(String prefix, Long partnerId) {
        return prefix + "-" + (1000 + increment(prefix, partnerId));
    }

    /** CP-0001, CP-0002 ... zero padded to width. */
    @Transactional
    public String nextPadded(String prefix, Long partnerId, int width) {
        return prefix + "-" + String.format("%0" + width + "d", increment(prefix, partnerId));
    }

    private long increment(String prefix, Long partnerId) {
        CodeSequence seq = repository.lockFor(partnerId, prefix).orElseGet(() -> {
            CodeSequence s = new CodeSequence();
            s.setChannelPartnerId(partnerId);
            s.setPrefix(prefix);
            s.setLastNo(0);
            return repository.saveAndFlush(s);
        });
        seq.setLastNo(seq.getLastNo() + 1);
        repository.save(seq);
        return seq.getLastNo();
    }
}
