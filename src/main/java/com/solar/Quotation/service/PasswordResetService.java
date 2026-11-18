package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.ForgotPasswordRequestDto;
import com.solar.Quotation.dto.requestdto.ResetPasswordRequestDto;

public interface PasswordResetService {

    /** Always returns normally, whether or not the account exists or the IP is throttled (no account probing). */
    void requestReset(ForgotPasswordRequestDto req, String clientIp);

    void resetPassword(ResetPasswordRequestDto req, String clientIp);
}