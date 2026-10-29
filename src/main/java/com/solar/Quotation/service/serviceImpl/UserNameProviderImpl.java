package com.solar.Quotation.service.serviceImpl;


import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.service.UserNameProvider;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserNameProviderImpl implements UserNameProvider {

    private final AppUserRepository repository;

    public UserNameProviderImpl(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> userIds) {
        Map<Long, String> out = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        // A partner user only ever sees the names of users of their own partner (never Super Admin or other partners).
        CurrentUser viewer = TenantContext.get();
        boolean restrict = viewer != null && viewer.getDataScope() != DataScope.ALL;
        for (AppUser u : repository.findAllById(userIds)) {
            if (restrict && !java.util.Objects.equals(u.getChannelPartnerId(), viewer.getPartnerId())) continue;
            out.put(u.getId(), u.getName());
        }
        return out;
    }
}