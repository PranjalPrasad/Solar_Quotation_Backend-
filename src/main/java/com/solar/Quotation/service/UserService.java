package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.AdminResetPasswordRequestDto;
import com.solar.Quotation.dto.requestdto.UserFilterRequestDto;
import com.solar.Quotation.dto.requestdto.UserRequestDto;
import com.solar.Quotation.dto.requestdto.UserStatusRequestDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.RoleRefResponseDto;
import com.solar.Quotation.dto.responsedto.UserResponseDto;
import com.solar.Quotation.dto.responsedto.UserSummaryResponseDto;

import java.util.List;

public interface UserService {

    PageResponse<UserResponseDto> list(UserFilterRequestDto filter);

    UserSummaryResponseDto summary();

    UserResponseDto get(Long id);

    UserResponseDto create(UserRequestDto req);

    UserResponseDto update(Long id, UserRequestDto req);

    UserResponseDto updateStatus(Long id, UserStatusRequestDto req);

    UserResponseDto resetPassword(Long id, AdminResetPasswordRequestDto req);

    void delete(Long id);

    /** Roles the caller may assign: Super Admin sees all, a partner admin only partner-level roles. */
    List<RoleRefResponseDto> roleOptions();
}
