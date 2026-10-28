package com.solar.Quotation.repository;


import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.entity.CustomerFollowup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReminderQueryRepository extends Repository<CustomerFollowup, Long> {

    /** Row: [followupId, channelPartnerId, customerId, createdBy, nextDate, customerName]. Pending follow-ups due today or earlier. */
    @Query("select f.id, f.channelPartnerId, f.customerId, f.createdBy, f.nextDate, c.name "
            + "from CustomerFollowup f, Customer c "
            + "where f.customerId = c.id and f.deleted = false and c.deleted = false "
            + "and f.status = :status and f.nextDate is not null and f.nextDate <= :today "
            + "order by f.id")
    List<Object[]> duePendingFollowups(@Param("status") FollowupStatus status,
                                       @Param("today") LocalDate today,
                                       Pageable pageable);

    /** Row: [quotationId, channelPartnerId, code, createdBy, validUntil, customerName]. Pending quotations expiring within the window. */
    @Query("select q.id, q.channelPartnerId, q.code, q.createdBy, q.validUntil, c.name "
            + "from Quotation q, Customer c "
            + "where q.customerId = c.id and q.deleted = false "
            + "and q.status = :status and q.validUntil >= :today and q.validUntil <= :until "
            + "order by q.id")
    List<Object[]> expiringQuotations(@Param("status") QuotationStatus status,
                                      @Param("today") LocalDate today,
                                      @Param("until") LocalDate until,
                                      Pageable pageable);
}
