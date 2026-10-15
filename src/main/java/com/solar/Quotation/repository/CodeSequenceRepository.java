package com.solar.Quotation.repository;

import com.solar.Quotation.entity.CodeSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CodeSequenceRepository extends JpaRepository<CodeSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CodeSequence s where s.channelPartnerId = :partnerId and s.prefix = :prefix")
    Optional<CodeSequence> lockFor(@Param("partnerId") Long partnerId, @Param("prefix") String prefix);
}
