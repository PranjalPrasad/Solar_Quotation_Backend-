package com.solar.Quotation.repository;
import com.solar.Quotation.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true where r.userId = :userId and r.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    /** Atomic one-time claim used by rotation: 1 = this caller revoked it, 0 = somebody already did (replay). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true where r.id = :id and r.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true where r.revoked = false and r.userId in "
            + "(select u.id from AppUser u where u.channelPartnerId = :pid)")
    int revokeAllForPartner(@Param("pid") Long partnerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken r set r.revoked = true where r.revoked = false and r.userId in "
            + "(select u.id from AppUser u where u.roleId = :rid)")
    int revokeAllForRole(@Param("rid") Long roleId);
}