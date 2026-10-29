package com.solar.Quotation.repository;

import com.solar.Quotation.entity.SubsidyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubsidyRuleRepository extends JpaRepository<SubsidyRule, Long> {

    List<SubsidyRule> findByChannelPartnerIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long channelPartnerId);

    List<SubsidyRule> findByChannelPartnerIdAndDeletedFalseAndActiveTrueOrderBySortOrderAscIdAsc(Long channelPartnerId);

    Optional<SubsidyRule> findByIdAndChannelPartnerIdAndDeletedFalse(Long id, Long channelPartnerId);
}