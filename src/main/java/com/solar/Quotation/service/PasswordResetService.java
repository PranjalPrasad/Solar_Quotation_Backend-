package com.solar.Quotation.service;
import com.solar.Quotation.dto.requestdto.ForgotPasswordRequestDto;
import com.solar.Quotation.dto.requestdto.ResetPasswordRequestDto;

public interface PasswordResetService {

    /** Always returns normally, whether or not the account exists (no account probing). */
    void requestReset(ForgotPasswordRequestDto req);

    void resetPassword(ResetPasswordRequestDto req);
}
