package com.solar.Quotation.repository;

import com.solar.Quotation.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByDeletedFalseOrderByIdAsc();

    Optional<Role> findByIdAndDeletedFalse(Long id);

    Optional<Role> findByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalse(String code);

    /** Native on purpose: AppUser entity is added in the next batch. Returns [role_id, count]. */
    @Query(value = "select role_id, count(*) from app_user where deleted = false group by role_id", nativeQuery = true)
    List<Object[]> countUsersGroupedByRole();

    @Query(value = "select count(*) from app_user where role_id = :roleId and deleted = false", nativeQuery = true)
    long countUsersByRoleId(@Param("roleId") Long roleId);
}
