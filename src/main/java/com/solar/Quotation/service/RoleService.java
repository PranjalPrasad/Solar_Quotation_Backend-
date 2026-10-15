package com.solar.Quotation.service;

import com.solar.Quotation.dto.requestdto.RolePermissionUpdateRequestDto;
import com.solar.Quotation.dto.requestdto.RoleRequestDto;
import com.solar.Quotation.dto.responsedto.ModuleResponseDto;
import com.solar.Quotation.dto.responsedto.PermissionMatrixResponseDto;
import com.solar.Quotation.dto.responsedto.RoleResponseDto;

import java.util.List;

public interface RoleService {

    List<RoleResponseDto> list();

    RoleResponseDto create(RoleRequestDto req);

    RoleResponseDto update(Long id, RoleRequestDto req);

    void delete(Long id);

    List<ModuleResponseDto> modules();

    PermissionMatrixResponseDto matrix();

    PermissionMatrixResponseDto.RoleGrant updatePermissions(Long id, RolePermissionUpdateRequestDto req);
}
