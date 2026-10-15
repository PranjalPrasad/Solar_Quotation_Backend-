package com.solar.Quotation.repository;


import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByIdAndDeletedFalse(Long id);

    Optional<AppUser> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByChannelPartnerIdAndDeletedFalse(Long channelPartnerId);

    /** Partner-aware fetch by id. A record of another partner is simply "not found". */
    @Query("select u from AppUser u where u.id = :id and u.deleted = false "
            + "and (:partnerId is null or u.channelPartnerId = :partnerId)")
    Optional<AppUser> findOne(@Param("id") Long id, @Param("partnerId") Long partnerId);

    @Query("select u from AppUser u where u.deleted = false "
            + "and (:partnerId is null or u.channelPartnerId = :partnerId) "
            + "and (:roleId is null or u.roleId = :roleId) "
            + "and (:status is null or u.status = :status) "
            + "and (:search is null or lower(u.name) like lower(concat('%', :search, '%')) "
            + "     or lower(u.username) like lower(concat('%', :search, '%')) "
            + "     or lower(u.email) like lower(concat('%', :search, '%')) "
            + "     or lower(u.code) like lower(concat('%', :search, '%')) "
            + "     or u.phone like concat('%', :search, '%'))")
    Page<AppUser> search(@Param("partnerId") Long partnerId,
                         @Param("roleId") Long roleId,
                         @Param("status") RecordStatus status,
                         @Param("search") String search,
                         Pageable pageable);

    @Query("select count(u) from AppUser u where u.deleted = false "
            + "and (:partnerId is null or u.channelPartnerId = :partnerId) "
            + "and (:status is null or u.status = :status)")
    long countFiltered(@Param("partnerId") Long partnerId, @Param("status") RecordStatus status);

    @Query("select count(u) from AppUser u, Role r where u.roleId = r.id and r.code = :roleCode and u.deleted = false "
            + "and (:partnerId is null or u.channelPartnerId = :partnerId) "
            + "and (:status is null or u.status = :status)")
    long countByRoleCode(@Param("roleCode") String roleCode,
                         @Param("partnerId") Long partnerId,
                         @Param("status") RecordStatus status);

    /** Rows of [channelPartnerId, count] for a page of partners (one query instead of one per partner). */
    @Query("select u.channelPartnerId, count(u) from AppUser u where u.deleted = false "
            + "and u.channelPartnerId in :partnerIds group by u.channelPartnerId")
    List<Object[]> countGroupedByPartner(@Param("partnerIds") Collection<Long> partnerIds);
}
