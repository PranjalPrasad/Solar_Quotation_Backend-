package com.solar.Quotation.repository;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.entity.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Every query takes partnerId (null = all partners, Super Admin only) and ownerId (null = whole partner,
 * set only for data scope OWN). The service gets both from TenantResolver; a controller never passes them.
 */
public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    @Query("select q from Quotation q where q.id = :id and q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId)")
    Optional<Quotation> findOne(@Param("id") Long id,
                                @Param("partnerId") Long partnerId,
                                @Param("ownerId") Long ownerId);

    @Query("select q from Quotation q where q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and (:status is null or q.status = :status) "
            + "and (:customerId is null or q.customerId = :customerId) "
            + "and (:referred is null or q.referred = :referred) "
            + "and (:fromDate is null or q.quoteDate >= :fromDate) "
            + "and (:toDate is null or q.quoteDate <= :toDate) "
            + "and (:search is null or lower(q.code) like lower(concat('%', :search, '%')) "
            + "     or lower(q.referredBy) like lower(concat('%', :search, '%')) "
            + "     or q.customerId in (select c.id from Customer c "
            + "                         where lower(c.name) like lower(concat('%', :search, '%')) "
            + "                            or c.mobile like concat('%', :search, '%')))")
    Page<Quotation> search(@Param("partnerId") Long partnerId,
                           @Param("ownerId") Long ownerId,
                           @Param("status") QuotationStatus status,
                           @Param("customerId") Long customerId,
                           @Param("referred") Boolean referred,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate,
                           @Param("search") String search,
                           Pageable pageable);

    @Query("select count(q) from Quotation q where q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and (:status is null or q.status = :status)")
    long countFiltered(@Param("partnerId") Long partnerId,
                       @Param("ownerId") Long ownerId,
                       @Param("status") QuotationStatus status);

    @Query("select coalesce(sum(q.totalAmount), 0) from Quotation q where q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and (:status is null or q.status = :status)")
    BigDecimal sumTotal(@Param("partnerId") Long partnerId,
                        @Param("ownerId") Long ownerId,
                        @Param("status") QuotationStatus status);

    long countByCustomerIdAndDeletedFalse(Long customerId);
}
