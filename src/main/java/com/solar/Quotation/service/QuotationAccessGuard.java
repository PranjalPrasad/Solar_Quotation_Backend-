package com.solar.Quotation.service;


/**
 * The history endpoint must show exactly the quotations the caller may already see
 * (Super Admin: all, Channel Partner: own only, ...). Implement this ONCE by delegating to your
 * existing quotation lookup, which already applies that scoping. Example:
 *
 * <pre>
 * &#64;Component
 * class QuotationAccessGuardImpl implements QuotationAccessGuard {
 *     private final QuotationService quotationService;
 *     QuotationAccessGuardImpl(QuotationService s) { this.quotationService = s; }
 *     public void assertCanView(Long id) { quotationService.getById(id); }   // throws 404/403 when not allowed
 * }
 * </pre>
 */
public interface QuotationAccessGuard {
    void assertCanView(Long quotationId);
}
