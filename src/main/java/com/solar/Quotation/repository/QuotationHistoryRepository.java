package com.solar.Quotation.repository;

import com.solar.Quotation.entity.QuotationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuotationHistoryRepository extends JpaRepository<QuotationHistory, Long> {

    List<QuotationHistory> findByQuotationIdOrderByVersionDesc(Long quotationId);

    @Query("select coalesce(max(h.version), 0) from QuotationHistory h where h.quotationId = :quotationId")
    int maxVersion(@Param("quotationId") Long quotationId);
}
