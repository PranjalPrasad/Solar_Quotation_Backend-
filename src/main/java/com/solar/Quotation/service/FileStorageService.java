package com.solar.Quotation.service;

import com.solar.Quotation.dto.responsedto.FileUploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /** Validates and stores an image under the CALLER's own partner. Returns the reference to save in the record. */
    FileUploadResponseDto store(MultipartFile file, String purpose);

    /**
     * Loads a stored file for the signed-in caller. Own partner's files only (Super Admin: any partner, read only).
     * Anything else - another partner's file, a malformed name, a missing file - is the same ResourceNotFoundException,
     * so a URL cannot be used to find out whether another partner's file exists.
     */
    StoredFile load(String partnerId, String folder, String name);

    /** Bytes are already verified against the file's own content, and contentType comes from that, never from the client. */
    record StoredFile(byte[] bytes, String contentType, String name) { }
}
