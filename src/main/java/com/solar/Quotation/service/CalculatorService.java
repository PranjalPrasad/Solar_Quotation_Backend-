package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.CalculationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationPreviewRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationRequestDto;
import com.solar.Quotation.dto.responsedto.CalculationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.CalculationResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;

public interface CalculatorService {

    /** Starting values for a new calculation, from the caller's own tenant settings. */
    CalculationDefaultsResponseDto defaults();

    /** Calculates with the caller's own tenant settings; saves and audits nothing. */
    CalculationResponseDto preview(CalculationPreviewRequestDto req);

    CalculationResponseDto create(CalculationRequestDto req);

    CalculationResponseDto update(Long id, CalculationRequestDto req);

    CalculationResponseDto getById(Long id);

    PageResponse<CalculationResponseDto> list(CalculationFilterRequestDto filter);

    void delete(Long id);
}