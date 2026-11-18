package com.solar.Quotation.controller;

import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.dto.responsedto.FileUploadResponseDto;
import com.solar.Quotation.service.FileStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Both endpoints need a valid Bearer token (SecurityConfig: anyRequest().authenticated()); there is NO public file
 * route. Tenant and permission checks are in FileStorageServiceImpl. See FILES.md.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService service;

    public FileController(FileStorageService service) {
        this.service = service;
    }

    /** multipart/form-data: file + purpose (LOGO | USER_PHOTO). A missing file is reported by the service as "Please choose a file". */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadResponseDto> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                                     @RequestParam(value = "purpose", required = false) String purpose) {
        return ApiResponse.ok(service.store(file, purpose), "File uploaded");
    }

    /**
     * Another partner's file, a malformed name and a missing file all answer 404. The browser cannot put a Bearer token
     * on an img tag, so the frontend fetches this with the token and shows the bytes as a blob (frontend/files.js).
     */
    @GetMapping("/{partnerId}/{folder}/{name:.+}")
    public ResponseEntity<byte[]> download(@PathVariable String partnerId,
                                           @PathVariable String folder,
                                           @PathVariable String name) {
        FileStorageService.StoredFile f = service.load(partnerId, folder, name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(f.bytes().length)
                // names are random and never reused, so a long PRIVATE cache is safe; never a shared cache
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePrivate())
                .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + f.name() + "\"")
                .body(f.bytes());
    }
}
