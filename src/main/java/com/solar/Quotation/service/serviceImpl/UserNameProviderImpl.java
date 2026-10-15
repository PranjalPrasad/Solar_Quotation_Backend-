package com.solar.Quotation.service.serviceImpl;


import com.solar.Quotation.entity.AppUser;
import com.solar.Quotation.repository.AppUserRepository;
import com.solar.Quotation.service.UserNameProvider;
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
        for (AppUser u : repository.findAllById(userIds)) {
            out.put(u.getId(), u.getName());
        }
        return out;
    }
}
