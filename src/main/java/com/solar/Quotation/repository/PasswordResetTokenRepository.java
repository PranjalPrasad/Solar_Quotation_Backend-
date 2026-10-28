package com.solar.Quotation.repository;


import com.solar.Quotation.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Used for the resend cool-down: was a link already issued to this user a moment ago? */
    boolean existsByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    /** Marks every unused link of the user as used (a new request replaces older links; a reset kills the rest). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.used = true where t.userId = :userId and t.used = false")
    int invalidateAllForUser(@Param("userId") Long userId);
}
