package com.solar.Quotation.repository;

import com.solar.Quotation.entity.AppModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface ModuleRepository extends JpaRepository<AppModule, Long> {

    List<AppModule> findByDeletedFalseOrderBySortOrderAsc();
}
