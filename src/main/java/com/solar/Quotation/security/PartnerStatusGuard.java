package com.solar.Quotation.security;

import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Har authenticated request par auth filter isko call kare: assertActive(user.getPartnerId()).
 * Inactive partner ke saare users turant 403 (JWT valid ho tab bhi). 5s cache; status change par evict().
 * Cache JVM-local hai: multi-instance deployment me max 5s delay.
 */
@Component
public class PartnerStatusGuard {

    private static final Logger log = LoggerFactory.getLogger(PartnerStatusGuard.class);
    private static final long TTL_MS = 5_000;

    private record Entry(boolean active, long expiresAt) { }

    private final ChannelPartnerRepository repository;
    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    public PartnerStatusGuard(ChannelPartnerRepository repository) {
        this.repository = repository;
    }

    public void assertActive(Long partnerId) {
        if (partnerId == null) {
            throw new ApiException(403, "Your organisation is deactivated");
        }
        long now = System.currentTimeMillis();
        Entry e = cache.get(partnerId);
        if (e == null || e.expiresAt() < now) {
            boolean active = repository.findByIdAndDeletedFalse(partnerId)
                    .map(p -> p.getStatus() == RecordStatus.ACTIVE).orElse(false);
            e = new Entry(active, now + TTL_MS);
            cache.put(partnerId, e);
        }
        if (!e.active()) {
            log.warn("[PARTNER] blocked request of inactive partnerId={}", partnerId);
            throw new ApiException(403, "Your organisation is deactivated");
        }
    }

    public void evict(Long partnerId) {
        cache.remove(partnerId);
    }
}