package com.solar.Quotation.repository;

import com.solar.Quotation.entity.GstRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GstRuleRepository extends JpaRepository<GstRule, Long> {

    List<GstRule> findByChannelPartnerIdAndDeletedFalseOrderByIdAsc(Long channelPartnerId);

    /** The partner id is part of the lookup, so a rule of another partner is simply "not found". */
    Optional<GstRule> findByIdAndChannelPartnerIdAndDeletedFalse(Long id, Long channelPartnerId);
}