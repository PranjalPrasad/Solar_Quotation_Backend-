package com.solar.Quotation.service;

import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Call these from the places that DEACTIVATE a user, a partner or a role (UserServiceImpl, ChannelPartnerServiceImpl,
 * RoleServiceImpl), in the same transaction as the status change. Blocking is already immediate without them
 * (JwtAuthFilter re-reads status on every request); this also kills refresh tokens and already-issued access tokens.
 */
@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public SessionRevocationService(AppUserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void revokeUser(Long userId, String reason) {
        AppUser u = userRepository.findByIdAndDeletedFalse(userId).orElse(null);
        if (u != null) {
            u.setTokenValidAfter(LocalDateTime.now(ZoneOffset.UTC));
            userRepository.save(u);
        }
        int n = refreshTokenRepository.revokeAllForUser(userId);
        log.info("[AUTH] sessions revoked scope=USER userId={} reason={} refreshTokens={}", userId, reason, n);
    }

    @Transactional
    public void revokePartner(Long partnerId, String reason) {
        int users = userRepository.invalidateAccessTokensForPartner(partnerId, LocalDateTime.now(ZoneOffset.UTC));
        int n = refreshTokenRepository.revokeAllForPartner(partnerId);
        log.info("[AUTH] sessions revoked scope=PARTNER partnerId={} reason={} users={} refreshTokens={}",
                partnerId, reason, users, n);
    }

    @Transactional
    public void revokeRole(Long roleId, String reason) {
        int users = userRepository.invalidateAccessTokensForRole(roleId, LocalDateTime.now(ZoneOffset.UTC));
        int n = refreshTokenRepository.revokeAllForRole(roleId);
        log.info("[AUTH] sessions revoked scope=ROLE roleId={} reason={} users={} refreshTokens={}",
                roleId, reason, users, n);
    }
}