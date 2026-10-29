package com.solar.Quotation.repository;

import com.solar.Quotation.entity.ProductStockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductStockMovementRepository extends JpaRepository<ProductStockMovement, Long> {

    List<ProductStockMovement> findTop200ByProductIdAndDeletedFalseOrderByCreatedAtDescIdDesc(Long productId);
}
