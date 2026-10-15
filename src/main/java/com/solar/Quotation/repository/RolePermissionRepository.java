package com.solar.Quotation.repository;
import com.solar.Quotation.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    @Query("select p.code from RolePermission rp, Permission p "
            + "where rp.permissionId = p.id and rp.roleId = :roleId and p.deleted = false")
    List<String> findPermissionCodesByRoleId(@Param("roleId") Long roleId);

    /** Mapping rows carry no business data, so they are replaced (hard deleted) when a role is re-granted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RolePermission rp where rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
