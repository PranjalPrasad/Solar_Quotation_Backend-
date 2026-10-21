package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.FilePurpose;
import com.solar.Quotation.dto.responsedto.FileUploadResponseDto;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.FileStorageService;
import com.solar.Quotation.util.ImageTypeDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores images on local disk under app.file.storage-dir (use a mounted volume or S3 in production).
 * The file name is generated (UUID), so a client can never choose a path, and the type is decided by the
 * file's own bytes. Files are served publicly by FileResourceConfig under /files/**; the UUID name is the
 * only secret, so do not upload anything confidential here.
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final Path root;
    private final String publicBaseUrl;
    private final PermissionCache permissionCache;

    public FileStorageServiceImpl(@Value("${app.file.storage-dir:./uploads}") String storageDir,
                                  @Value("${app.file.public-base-url:}") String publicBaseUrl,
                                  PermissionCache permissionCache) {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        this.permissionCache = permissionCache;
    }

    @Override
    public FileUploadResponseDto store(MultipartFile file, String purposeValue) {
        FilePurpose purpose = parsePurpose(purposeValue);
        authorize(purpose);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Please choose a file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("VALIDATION_ERROR", "The image is too large (maximum 2 MB)");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("FILE_ERROR", "Could not read the uploaded file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String ext = ImageTypeDetector.detect(bytes);
        if (ext == null) {
            throw new BusinessException("VALIDATION_ERROR", "Only PNG, JPG and WEBP images are allowed");
        }

        String folder = purpose == FilePurpose.LOGO ? "branding" : "users";
        String fileName = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(folder).resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("VALIDATION_ERROR", "Invalid file location");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new BusinessException("FILE_ERROR", "Could not save the file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String path = "/files/" + folder + "/" + fileName;
        return new FileUploadResponseDto(path, publicBaseUrl + path, bytes.length, ImageTypeDetector.contentType(ext));
    }

    private FilePurpose parsePurpose(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "purpose is required (LOGO or USER_PHOTO)");
        }
        try {
            return FilePurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown purpose " + value);
        }
    }

    private void authorize(FilePurpose purpose) {
        CurrentUser user = TenantContext.get();
        if (user == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        Set<String> granted = permissionCache.permissionsForRole(user.getRoleId());
        boolean allowed = switch (purpose) {
            case LOGO -> granted.contains("SETTINGS:EDIT");
            case USER_PHOTO -> granted.contains("USER:CREATE") || granted.contains("USER:EDIT");
        };
        if (!allowed) {
            throw new ForbiddenException("FORBIDDEN", "You do not have permission to upload this kind of file");
        }
    }
}
