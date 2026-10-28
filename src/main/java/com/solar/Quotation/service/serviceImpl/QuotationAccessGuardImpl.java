package com.solar.Quotation.service.serviceImpl;
import com.solar.Quotation.service.QuotationAccessGuard;
import com.solar.Quotation.service.QuotationService;
import org.springframework.stereotype.Component;

@Component
public class QuotationAccessGuardImpl implements QuotationAccessGuard {

    private final QuotationService quotationService;

    public QuotationAccessGuardImpl(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    @Override
    public void assertCanView(Long quotationId) {
        // get() already applies the tenant/role scoping and throws 404/403 when not allowed
        quotationService.get(quotationId);
    }
}
