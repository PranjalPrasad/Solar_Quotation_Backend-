package com.solar.Quotation.repository;

import com.solar.Quotation.entity.PartnerAllowedRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface PartnerAllowedRoleRepository extends JpaRepository<PartnerAllowedRole, Long> {

    boolean existsByChannelPartnerIdAndRoleId(Long channelPartnerId, Long roleId);

    @Query("select a.roleId from PartnerAllowedRole a where a.channelPartnerId = :pid")
    Set<Long> roleIdsForPartner(@Param("pid") Long partnerId);

    /** Same as roleIdsForPartner; name used by UserServiceImpl / PartnerAllowedRoleService. */
    @Query("select a.roleId from PartnerAllowedRole a where a.channelPartnerId = :pid")
    Set<Long> findRoleIds(@Param("pid") Long partnerId);

    @Query("select count(a) > 0 from PartnerAllowedRole a where a.channelPartnerId = :pid and a.roleId = :rid")
    boolean existsByPartnerIdAndRoleId(@Param("pid") Long partnerId, @Param("rid") Long roleId);

    @Query("select count(u) from AppUser u where u.channelPartnerId = :pid and u.roleId = :rid and u.deleted = false")
    long countUsersWithRoleInPartner(@Param("pid") Long partnerId, @Param("rid") Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PartnerAllowedRole a where a.channelPartnerId = :pid")
    void deleteByPartner(@Param("pid") Long partnerId);
}