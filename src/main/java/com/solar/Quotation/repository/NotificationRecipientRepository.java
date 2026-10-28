package com.solar.Quotation.repository;


import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.AppUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRecipientRepository extends Repository<AppUser, Long> {

    @Query("select u.id from AppUser u, Role r where u.roleId = r.id and r.code = :roleCode "
            + "and u.deleted = false and u.status = :status and u.channelPartnerId = :partnerId")
    List<Long> activeUserIdsByPartnerAndRole(@Param("partnerId") Long partnerId,
                                             @Param("roleCode") String roleCode,
                                             @Param("status") RecordStatus status);

    @Query("select u.id from AppUser u, Role r where u.roleId = r.id and r.code = :roleCode "
            + "and u.deleted = false and u.status = :status")
    List<Long> activeUserIdsByRole(@Param("roleCode") String roleCode,
                                   @Param("status") RecordStatus status);
}
