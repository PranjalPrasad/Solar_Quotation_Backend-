package com.solar.Quotation.repository;

import com.solar.Quotation.entity.QuotationHistory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Deliberately NOT a JpaRepository: findAll / findById / delete are not available, so history can only be
 * read with a partner id and is append-only. Every read takes the partner of the (already tenant-checked) quotation.
 */
public interface QuotationHistoryRepository extends Repository<QuotationHistory, Long> {

    QuotationHistory save(QuotationHistory row);

    List<QuotationHistory> findByQuotationIdAndChannelPartnerIdOrderByVersionDesc(Long quotationId, Long channelPartnerId);

    @Query("select coalesce(max(h.version), 0) from QuotationHistory h "
            + "where h.quotationId = :quotationId and h.channelPartnerId = :partnerId")
    int maxVersion(@Param("quotationId") Long quotationId, @Param("partnerId") Long partnerId);
}