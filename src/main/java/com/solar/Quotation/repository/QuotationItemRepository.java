package com.solar.Quotation.repository;

import com.solar.Quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Items are reachable ONLY through a quotation the caller already passed the tenant check for.
 * Not a JpaRepository: findAll / findById / deleteById do not exist, and every method takes the partner id of that
 * quotation, so an item id or a quotation id on its own can never reach another partner's lines.
 */
public interface QuotationItemRepository extends Repository<QuotationItem, Long> {

    List<QuotationItem> saveAll(Iterable<QuotationItem> items);

    @Query("select i from QuotationItem i where i.quotationId = :quotationId and i.channelPartnerId = :partnerId "
            + "and i.deleted = false order by i.sortOrder asc")
    List<QuotationItem> findForQuotation(@Param("quotationId") Long quotationId, @Param("partnerId") Long partnerId);

    /** Items are children, not business records: on edit the old lines are removed and the new set inserted. */
    @Modifying(flushAutomatically = true)
    @Query("delete from QuotationItem i where i.quotationId = :quotationId and i.channelPartnerId = :partnerId")
    void deleteForQuotation(@Param("quotationId") Long quotationId, @Param("partnerId") Long partnerId);
}