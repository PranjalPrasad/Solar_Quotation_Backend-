package com.solar.Quotation.repository;

import com.solar.Quotation.Enum.ProductCategory;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Products are a catalog shared by everybody of ONE partner, so there is no ownerId filter here
 * (a data scope of OWN must not hide products created by the partner admin).
 * stockMode: 0 = any, 1 = out of stock, 2 = low stock, 3 = in stock.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p where p.id = :id and p.deleted = false "
            + "and (:partnerId is null or p.channelPartnerId = :partnerId)")
    Optional<Product> findOne(@Param("id") Long id, @Param("partnerId") Long partnerId);

    @Query("select p from Product p where p.deleted = false "
            + "and (:partnerId is null or p.channelPartnerId = :partnerId) "
            + "and (:category is null or p.category = :category) "
            + "and (:status is null or p.status = :status) "
            + "and (:gst is null or p.gstPercent = :gst) "
            + "and (:stockMode = 0 "
            + "     or (:stockMode = 1 and p.stock <= 0) "
            + "     or (:stockMode = 2 and p.stock > 0 and p.stock <= p.lowStockThreshold) "
            + "     or (:stockMode = 3 and p.stock > p.lowStockThreshold)) "
            + "and (:search is null or lower(p.name) like lower(concat('%', :search, '%')) "
            + "     or lower(p.brand) like lower(concat('%', :search, '%')) "
            + "     or lower(p.sku) like lower(concat('%', :search, '%')) "
            + "     or lower(p.code) like lower(concat('%', :search, '%')))")
    Page<Product> search(@Param("partnerId") Long partnerId,
                         @Param("category") ProductCategory category,
                         @Param("status") RecordStatus status,
                         @Param("gst") BigDecimal gst,
                         @Param("stockMode") int stockMode,
                         @Param("search") String search,
                         Pageable pageable);

    /** Light list for the quotation product picker. */
    @Query("select p from Product p where p.deleted = false and p.status = :status "
            + "and (:partnerId is null or p.channelPartnerId = :partnerId) "
            + "order by p.category asc, p.name asc")
    List<Product> findCatalog(@Param("partnerId") Long partnerId,
                              @Param("status") RecordStatus status,
                              Pageable pageable);

    @Query("select count(p) from Product p where p.deleted = false "
            + "and (:partnerId is null or p.channelPartnerId = :partnerId) "
            + "and (:status is null or p.status = :status) "
            + "and (:stockMode = 0 "
            + "     or (:stockMode = 1 and p.stock <= 0) "
            + "     or (:stockMode = 2 and p.stock > 0 and p.stock <= p.lowStockThreshold) "
            + "     or (:stockMode = 3 and p.stock > p.lowStockThreshold))")
    long countFiltered(@Param("partnerId") Long partnerId,
                       @Param("status") RecordStatus status,
                       @Param("stockMode") int stockMode);

    /** Null when there are no products. */
    @Query("select sum(p.price * p.stock) from Product p where p.deleted = false "
            + "and (:partnerId is null or p.channelPartnerId = :partnerId)")
    BigDecimal sumInventoryValue(@Param("partnerId") Long partnerId);

    boolean existsByChannelPartnerIdAndSkuAndDeletedFalse(Long channelPartnerId, String sku);

    boolean existsByChannelPartnerIdAndSkuAndDeletedFalseAndIdNot(Long channelPartnerId, String sku, Long id);
}
