package com.solar.Quotation.security;
import com.solar.Quotation.repository.RolePermissionRepository;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PermissionCache {

    private final RolePermissionRepository rolePermissionRepository;
    private final ConcurrentHashMap<Long, Set<String>> cache = new ConcurrentHashMap<>();

    public PermissionCache(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public Set<String> permissionsForRole(Long roleId) {
        return cache.computeIfAbsent(roleId,
                id -> Set.copyOf(rolePermissionRepository.findPermissionCodesByRoleId(id)));
    }

    public void evict(Long roleId) { cache.remove(roleId); }

    public void evictAll() { cache.clear(); }
}
