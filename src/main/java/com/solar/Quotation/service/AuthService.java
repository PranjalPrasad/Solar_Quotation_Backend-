package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.ChangePasswordRequestDto;
import com.solar.Quotation.dto.requestdto.LoginRequestDto;
import com.solar.Quotation.dto.requestdto.RefreshTokenRequestDto;
import com.solar.Quotation.dto.responsedto.CurrentUserResponseDto;
import com.solar.Quotation.dto.responsedto.LoginResponseDto;
import com.solar.Quotation.dto.responsedto.TokenResponseDto;

public interface AuthService {

    /** clientIp is request.getRemoteAddr() (see README: configure the proxy, never trust X-Forwarded-For blindly). */
    LoginResponseDto login(LoginRequestDto req, String clientIp);

    TokenResponseDto refresh(RefreshTokenRequestDto req);

    void logout(RefreshTokenRequestDto req);

    CurrentUserResponseDto me();

    void changePassword(ChangePasswordRequestDto req);
}