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

public interface CustomerFollowupRepository extends JpaRepository<CustomerFollowup, Long> {

    List<CustomerFollowup> findByCustomerIdAndDeletedFalseOrderByFollowupDateDescIdDesc(Long customerId);

    Optional<CustomerFollowup> findByIdAndCustomerIdAndDeletedFalse(Long id, Long customerId);

    @Query("select f from CustomerFollowup f where f.deleted = false "
            + "and (:partnerId is null or f.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or f.createdBy = :ownerId) "
            + "and (:customerId is null or f.customerId = :customerId) "
            + "and (:stored is null or f.status = :stored) "
            + "and (:mode = 0 or (:mode = 1 and f.nextDate < :today) "
            + "     or (:mode = 2 and (f.nextDate is null or f.nextDate >= :today))) "
            + "and (:search is null or lower(f.remark) like lower(concat('%', :search, '%')) "
            + "     or f.customerId in (select c.id from Customer c "
            + "                         where lower(c.name) like lower(concat('%', :search, '%'))))")
    Page<CustomerFollowup> search(@Param("partnerId") Long partnerId,
                                  @Param("ownerId") Long ownerId,
                                  @Param("customerId") Long customerId,
                                  @Param("stored") FollowupStatus stored,
                                  @Param("mode") int mode,
                                  @Param("today") LocalDate today,
                                  @Param("search") String search,
                                  Pageable pageable);
}
