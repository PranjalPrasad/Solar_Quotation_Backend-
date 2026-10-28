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
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt");

    private final NotificationRepository repository;

    public NotificationServiceImpl(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponseDto> list(NotificationFilterRequestDto f) {
        Long userId = currentUserId();
        Pageable pageable = f.toPageable("createdAt,desc", SORT_FIELDS);
        Page<Notification> page = repository.search(userId, f.isUnreadOnly() ? 1 : 0, parseType(f.getType()), pageable);
        return PageResponse.of(page, this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponseDto unreadCount() {
        return new UnreadCountResponseDto(repository.countUnread(currentUserId()));
    }

    @Override
    @Transactional
    public UnreadCountResponseDto markRead(Long id) {
        Long userId = currentUserId();
        if (repository.markRead(id, userId, nowUtc()) == 0) {
            throw new ResourceNotFoundException("Notification not found");
        }
        return new UnreadCountResponseDto(repository.countUnread(userId));
    }

    @Override
    @Transactional
    public UnreadCountResponseDto markAllRead() {
        Long userId = currentUserId();
        repository.markAllRead(userId, nowUtc());
        return new UnreadCountResponseDto(repository.countUnread(userId));
    }

    @Override
    @Transactional
    public UnreadCountResponseDto delete(Long id) {
        Long userId = currentUserId();
        Notification n = repository.findByIdAndRecipientUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.setDeleted(true);
        repository.save(n);
        return new UnreadCountResponseDto(repository.countUnread(userId));
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
