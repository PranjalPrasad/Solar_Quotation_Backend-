package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.AccessResponseDto;

public interface AccessService {

    /** Modules (with the allowed actions) and permission codes of the current user's role. */
    AccessResponseDto currentAccess();
}