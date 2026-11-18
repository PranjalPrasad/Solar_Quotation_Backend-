package com.solar.Quotation.repository;


import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Who may receive a notification. Every query is pinned to ONE tenant: there is deliberately no "all users with
 * role X" query any more. Super Admins are found as the SUPER_ADMIN users of the PLATFORM partner.
 */
public interface NotificationRecipientRepository extends Repository<AppUser, Long> {

    @Query("select u.id from AppUser u, Role r where u.roleId = r.id and r.code = :roleCode "
            + "and u.deleted = false and u.status = :status and u.channelPartnerId = :partnerId")
    List<Long> activeUserIdsByPartnerAndRole(@Param("partnerId") Long partnerId,
                                             @Param("roleCode") String roleCode,
                                             @Param("status") RecordStatus status);

    /** Of the given user ids, the ones that really belong to this tenant (and are not deleted). */
    @Query("select u.id from AppUser u where u.deleted = false and u.channelPartnerId = :partnerId "
            + "and u.id in :userIds")
    List<Long> userIdsInPartner(@Param("partnerId") Long partnerId, @Param("userIds") Collection<Long> userIds);
}
