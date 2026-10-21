package com.solar.Quotation.repository;


import com.solar.Quotation.entity.SubsidyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubsidyRuleRepository extends JpaRepository<SubsidyRule, Long> {

    List<SubsidyRule> findByDeletedFalseOrderBySortOrderAscIdAsc();

    List<SubsidyRule> findByDeletedFalseAndActiveTrueOrderBySortOrderAscIdAsc();

    Optional<SubsidyRule> findByIdAndDeletedFalse(Long id);
}
