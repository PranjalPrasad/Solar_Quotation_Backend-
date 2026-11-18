package com.solar.Quotation.service.serviceImpl;
import com.solar.Quotation.Enum.NotificationType;
import com.solar.Quotation.dto.requestdto.NotificationFilterRequestDto;
import com.solar.Quotation.dto.responsedto.NotificationResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.UnreadCountResponseDto;
import com.solar.Quotation.entity.Notification;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.NotificationRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * "My notifications": every call is pinned to the caller's OWN tenant AND the caller's user id.
 * The tenant is never taken from the request (no ?partnerId=, no ?viewPartnerId=): a Super Admin's bell shows
 * the Super Admin's PLATFORM-tenant notifications, whichever partner they are currently viewing.
 *
 * Logging ([NOTIFY], ids only): the bell polls every 30 s per browser, so reads are DEBUG; changes are INFO/DEBUG;
 * a row that is not the caller's (other user or other tenant, both look like 404) is WARN.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("createdAt");

    private final NotificationRepository repository;
    private final TenantResolver tenant;

    public NotificationServiceImpl(NotificationRepository repository, TenantResolver tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponseDto> list(NotificationFilterRequestDto f) {
        Long partnerId = tenant.ownPartnerId();
        Long userId = currentUserId();
        Pageable pageable = f.toPageable("createdAt,desc", SORT_FIELDS);
        Page<Notification> page = repository.search(partnerId, userId, f.isUnreadOnly() ? 1 : 0, parseType(f.getType()), pageable);
        log.debug("[NOTIFY] list unreadOnly={} page={} size={} rows={} partnerId={} userId={}",
                f.isUnreadOnly(), pageable.getPageNumber(), pageable.getPageSize(), page.getNumberOfElements(), partnerId, userId);
        return PageResponse.of(page, this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponseDto unreadCount() {
        Long partnerId = tenant.ownPartnerId();
        Long userId = currentUserId();
        long unread = repository.countUnread(partnerId, userId);
        log.debug("[NOTIFY] unread-count unread={} partnerId={} userId={}", unread, partnerId, userId);
        return new UnreadCountResponseDto(unread);
    }

    @Override
    @Transactional
    public UnreadCountResponseDto markRead(Long id) {
        Long partnerId = tenant.ownPartnerId();
        Long userId = currentUserId();
        if (repository.markRead(id, partnerId, userId, nowUtc()) == 0) {
            log.warn("[NOTIFY] markRead not found id={} {}", id, Actors.describe());
            throw new ResourceNotFoundException("Notification not found");
        }
        log.debug("[NOTIFY] markRead id={} partnerId={} userId={}", id, partnerId, userId);
        return new UnreadCountResponseDto(repository.countUnread(partnerId, userId));
    }

    @Override
    @Transactional
    public UnreadCountResponseDto markAllRead() {
        Long partnerId = tenant.ownPartnerId();
        Long userId = currentUserId();
        int changed = repository.markAllRead(partnerId, userId, nowUtc());
        log.info("[NOTIFY] markAllRead changed={} partnerId={} userId={}", changed, partnerId, userId);
        return new UnreadCountResponseDto(repository.countUnread(partnerId, userId));
    }

    @Override
    @Transactional
    public UnreadCountResponseDto delete(Long id) {
        Long partnerId = tenant.ownPartnerId();
        Long userId = currentUserId();
        Notification n = repository.findByIdAndChannelPartnerIdAndRecipientUserIdAndDeletedFalse(id, partnerId, userId)
                .orElseThrow(() -> {
                    log.warn("[NOTIFY] delete not found id={} {}", id, Actors.describe());
                    return new ResourceNotFoundException("Notification not found");
                });
        n.setDeleted(true);
        repository.save(n);
        log.debug("[NOTIFY] delete id={} partnerId={} userId={}", id, partnerId, userId);
        return new UnreadCountResponseDto(repository.countUnread(partnerId, userId));
    }

    // ---------------------------------------------------------------- helpers

    private Long currentUserId() {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        return u.getUserId();
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static NotificationType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return NotificationType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown notification type: " + raw);
        }
    }

    private NotificationResponseDto toDto(Notification n) {
        NotificationResponseDto d = new NotificationResponseDto();
        d.setId(n.getId());
        d.setType(n.getType().name());
        d.setSeverity(n.getSeverity().name());
        d.setTitle(n.getTitle());
        d.setMessage(n.getMessage());
        d.setEntityType(n.getEntityType());
        d.setEntityId(n.getEntityId());
        d.setPartnerId(n.getChannelPartnerId());
        d.setRead(n.getReadAt() != null);
        d.setReadAt(n.getReadAt() == null ? null : n.getReadAt().toInstant(ZoneOffset.UTC).toString());
        d.setCreatedAt(n.getCreatedAt() == null ? null : n.getCreatedAt().toInstant(ZoneOffset.UTC).toString());
        return d;
    }
}
