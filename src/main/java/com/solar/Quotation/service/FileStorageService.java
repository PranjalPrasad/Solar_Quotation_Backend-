package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.FileUploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    FileUploadResponseDto store(MultipartFile file, String purpose);
}
