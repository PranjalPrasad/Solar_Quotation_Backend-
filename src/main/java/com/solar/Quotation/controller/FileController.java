package com.solar.Quotation.controller;

import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.FileUploadResponseDto;
import com.solar.Quotation.service.FileStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService service;

    public FileController(FileStorageService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponseDto> upload(@RequestParam("file") MultipartFile file,
                                                     @RequestParam("purpose") String purpose) {
        return ApiResponse.ok(service.store(file, purpose), "File uploaded");
    }
}
