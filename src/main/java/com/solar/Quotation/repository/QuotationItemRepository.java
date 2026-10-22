package com.solar.Quotation.repository;

import com.solar.Quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Items are only ever read or written after the parent quotation passed the tenant check in QuotationRepository. */
public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {

    List<QuotationItem> findByQuotationIdOrderBySortOrderAsc(Long quotationId);

    List<QuotationItem> findByQuotationIdInOrderByQuotationIdAscSortOrderAsc(Collection<Long> quotationIds);

    /** Items are children, not business records: on edit the old lines are removed and the new set inserted. */
    @Modifying(flushAutomatically = true)
    @Query("delete from QuotationItem i where i.quotationId = :quotationId")
    void deleteByQuotationId(@Param("quotationId") Long quotationId);
}
