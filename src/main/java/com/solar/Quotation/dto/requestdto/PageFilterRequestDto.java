package com.solar.Quotation.dto.requestdto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PageFilterRequestDto {

    public static final int MAX_SIZE = 100;

    private int page = 0;
    private int size = 10;
    private String sort;
    private String search;
    private Long partnerId;   // honoured for SUPER_ADMIN only (TenantResolver decides)

    public Pageable toPageable(String defaultSort, Set<String> allowedSortFields) {
        int s = Math.min(Math.max(size, 1), MAX_SIZE);
        int p = Math.max(page, 0);
        String spec = (sort == null || sort.isBlank()) ? defaultSort : sort;
        String[] parts = spec.split(",");
        String field = parts[0].trim();
        if (!allowedSortFields.contains(field)) {
            field = defaultSort.split(",")[0].trim();
        }
        Sort.Direction dir = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(p, s, Sort.by(dir, field));
    }

    /** Null when blank so JPQL (:search is null) works. */
    public String getSearchOrNull() {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
}
