package com.solar.Quotation.repository;

import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.entity.CustomerFollowup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Follow-ups are reachable only THROUGH a customer the caller may see:
 *  - the by-customer methods also pin the partner id (the service has already tenant-checked the customer);
 *  - search() keeps a follow-up only when its customer is in the same tenant, not deleted, and (data scope OWN)
 *    created by the caller. So an OWN user never sees a follow-up of a customer that is not theirs.
 */
public interface CustomerFollowupRepository extends JpaRepository<CustomerFollowup, Long> {

    List<CustomerFollowup> findByCustomerIdAndChannelPartnerIdAndDeletedFalseOrderByFollowupDateDescIdDesc(
            Long customerId, Long channelPartnerId);

    Optional<CustomerFollowup> findByIdAndCustomerIdAndChannelPartnerIdAndDeletedFalse(
            Long id, Long customerId, Long channelPartnerId);

    @Query("select f from CustomerFollowup f where f.deleted = false "
            + "and f.channelPartnerId = :partnerId "
            + "and f.customerId in (select c.id from Customer c where c.deleted = false "
            + "                     and c.channelPartnerId = :partnerId "
            + "                     and (:ownerId is null or c.createdBy = :ownerId)) "
            + "and (:customerId is null or f.customerId = :customerId) "
            + "and (:stored is null or f.status = :stored) "
            + "and (:mode = 0 or (:mode = 1 and f.nextDate < :today) "
            + "     or (:mode = 2 and (f.nextDate is null or f.nextDate >= :today))) "
            + "and (:search is null or lower(f.remark) like lower(concat('%', :search, '%')) "
            + "     or f.customerId in (select c2.id from Customer c2 "
            + "                         where c2.channelPartnerId = :partnerId "
            + "                           and lower(c2.name) like lower(concat('%', :search, '%'))))")
    Page<CustomerFollowup> search(@Param("partnerId") Long partnerId,
                                  @Param("ownerId") Long ownerId,
                                  @Param("customerId") Long customerId,
                                  @Param("stored") FollowupStatus stored,
                                  @Param("mode") int mode,
                                  @Param("today") LocalDate today,
                                  @Param("search") String search,
                                  Pageable pageable);
}