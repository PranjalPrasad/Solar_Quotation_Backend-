package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.LoginRequest;
import com.solar.Quotation.dto.responsedto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
