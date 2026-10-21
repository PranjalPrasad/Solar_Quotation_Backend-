package com.solar.Quotation.repository;

import com.solar.Quotation.entity.GstRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GstRuleRepository extends JpaRepository<GstRule, Long> {

    List<GstRule> findByDeletedFalseOrderByIdAsc();

    Optional<GstRule> findByIdAndDeletedFalse(Long id);
}
