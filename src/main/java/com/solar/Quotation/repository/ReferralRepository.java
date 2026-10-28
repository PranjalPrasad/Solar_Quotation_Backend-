package com.solar.Quotation.repository;

import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.entity.Referral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Optional<Referral> findByQuotationIdAndDeletedFalse(Long quotationId);

    @Query("select r from Referral r where r.id = :id and r.deleted = false "
            + "and (:ownerPartnerId is null or r.channelPartnerId = :ownerPartnerId) "
            + "and (:referrerPartnerId is null or r.referrerPartnerId = :referrerPartnerId)")
    Optional<Referral> findOne(@Param("id") Long id,
                               @Param("ownerPartnerId") Long ownerPartnerId,
                               @Param("referrerPartnerId") Long referrerPartnerId);

    /** searchCustomer is false for partner users, so they cannot probe the customers of another partner. */
    @Query("select r from Referral r where r.deleted = false "
            + "and (:ownerPartnerId is null or r.channelPartnerId = :ownerPartnerId) "
            + "and (:referrerPartnerId is null or r.referrerPartnerId = :referrerPartnerId) "
            + "and (:status is null or r.status = :status) "
            + "and r.quoteDate >= :fromDate and r.quoteDate <= :toDate "
            + "and (:search is null or lower(r.code) like lower(concat('%', :search, '%')) "
            + "     or lower(r.quotationCode) like lower(concat('%', :search, '%')) "
            + "     or lower(r.referrerName) like lower(concat('%', :search, '%')) "
            + "     or (:searchCustomer = true and r.customerId in (select c.id from Customer c "
            + "                         where lower(c.name) like lower(concat('%', :search, '%')))))")
    Page<Referral> search(@Param("ownerPartnerId") Long ownerPartnerId,
                          @Param("referrerPartnerId") Long referrerPartnerId,
                          @Param("status") ReferralStatus status,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate,
                          @Param("search") String search,
                          @Param("searchCustomer") boolean searchCustomer,
                          Pageable pageable);

    /** Rows of [status, count, sum(referralAmount)]. */
    @Query("select r.status, count(r), coalesce(sum(r.referralAmount), 0) from Referral r where r.deleted = false "
            + "and (:ownerPartnerId is null or r.channelPartnerId = :ownerPartnerId) "
            + "and (:referrerPartnerId is null or r.referrerPartnerId = :referrerPartnerId) "
            + "group by r.status")
    List<Object[]> statusTotals(@Param("ownerPartnerId") Long ownerPartnerId,
                                @Param("referrerPartnerId") Long referrerPartnerId);

    @Query("select coalesce(sum(r.referralAmount), 0) from Referral r where r.deleted = false "
            + "and (:ownerPartnerId is null or r.channelPartnerId = :ownerPartnerId) "
            + "and (:referrerPartnerId is null or r.referrerPartnerId = :referrerPartnerId) "
            + "and r.status = :status and r.paidOn >= :fromDate and r.paidOn <= :toDate")
    java.math.BigDecimal sumPaidBetween(@Param("ownerPartnerId") Long ownerPartnerId,
                                        @Param("referrerPartnerId") Long referrerPartnerId,
                                        @Param("status") ReferralStatus status,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

    /** Rows of [referrerPartnerId, status, count, sum(referralAmount)] for the partner list column. */
    @Query("select r.referrerPartnerId, r.status, count(r), coalesce(sum(r.referralAmount), 0) from Referral r "
            + "where r.deleted = false and r.referrerPartnerId in :partnerIds "
            + "group by r.referrerPartnerId, r.status")
    List<Object[]> totalsByReferrer(@Param("partnerIds") Collection<Long> partnerIds);
}
