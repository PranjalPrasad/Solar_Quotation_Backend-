package com.solar.Quotation.repository;

import com.solar.Quotation.entity.LoginThrottle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoginThrottleRepository extends JpaRepository<LoginThrottle, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LoginThrottle t where t.scope = :scope and t.subjectHash = :hash")
    Optional<LoginThrottle> findForUpdate(@Param("scope") String scope, @Param("hash") String hash);

    Optional<LoginThrottle> findByScopeAndSubjectHash(String scope, String subjectHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LoginThrottle t where t.scope = :scope and t.subjectHash = :hash")
    int deleteOne(@Param("scope") String scope, @Param("hash") String hash);
}