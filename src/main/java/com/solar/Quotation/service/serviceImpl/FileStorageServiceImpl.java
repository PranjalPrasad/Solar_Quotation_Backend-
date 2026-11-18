package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.FilePurpose;
import com.solar.Quotation.dto.responsedto.FileUploadResponseDto;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.FileStorageService;
import com.solar.Quotation.util.FilePaths;
import com.solar.Quotation.util.ImageDimensions;
import com.solar.Quotation.util.ImageTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Image storage on local disk (mount a volume at FILE_STORAGE_PATH, or replace this class with an S3 one).
 *
 *   - OUTSIDE the web root: nothing under storage-dir is a Spring resource; files leave only through
 *     GET /api/files/{partnerId}/{folder}/{name}, which needs a valid token and the caller's own partner.
 *   - The name is a server-made UUID, the client file name is never used; the folder is the caller's partner id from
 *     the token (never from the request); the type is decided by the file's own bytes (magic bytes), PNG/JPG/WEBP only.
 *   - Size is capped (app.file.max-bytes) and the pixel dimensions are checked from the header.
 *
 * LOGGING ([FILE], Module 0): purpose, size, userId, partnerId, reason code and the generated file id. Never the file
 * contents, the client's file name or a disk path.
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);
    static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;

    private final Path root;
    private final long maxBytes;
    private final PermissionCache permissionCache;

    public FileStorageServiceImpl(@Value("${app.file.storage-dir:./uploads}") String storageDir,
                                  @Value("${app.file.max-bytes:2097152}") long maxBytes,
                                  PermissionCache permissionCache,
                                  Environment env) {
        Path configured = Paths.get(storageDir);
        boolean prod = env != null && env.acceptsProfiles(Profiles.of("prod", "production"));
        if (prod && !configured.isAbsolute()) {
            throw new IllegalStateException("[FILE] Refusing to start in prod: FILE_STORAGE_PATH must be an absolute path "
                    + "to a mounted volume (it is '" + storageDir + "')");
        }
        this.root = configured.toAbsolutePath().normalize();
        this.maxBytes = maxBytes > 0 ? maxBytes : DEFAULT_MAX_BYTES;
        this.permissionCache = permissionCache;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("[FILE] Cannot create the upload folder (check FILE_STORAGE_PATH and its permissions)", e);
        }
        if (!Files.isWritable(root)) {
            throw new IllegalStateException("[FILE] The upload folder is not writable (check FILE_STORAGE_PATH and its permissions)");
        }
        log.info("[FILE] storage ready maxBytes={} prod={}", this.maxBytes, prod);
    }

    // ------------------------------------------------------------------ upload

    @Override
    public FileUploadResponseDto store(MultipartFile file, String purposeValue) {
        CurrentUser user = TenantContext.get();
        if (user == null || user.getPartnerId() == null) {
            logReject(null, "UNAUTHENTICATED", file == null ? 0 : file.getSize(), user);
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        long declaredSize = file == null ? 0 : file.getSize();

        FilePurpose purpose = parsePurpose(purposeValue);
        if (purpose == null) {
            logReject(null, "PURPOSE", declaredSize, user);
            throw new BusinessException("VALIDATION_ERROR", "purpose is required (LOGO or USER_PHOTO)");
        }
        if (!isAllowed(purpose, user)) {
            logReject(purpose, "FORBIDDEN", declaredSize, user);
            throw new ForbiddenException("FORBIDDEN", "You do not have permission to upload this kind of file");
        }
        if (file == null || file.isEmpty()) {
            logReject(purpose, "EMPTY", 0, user);
            throw new BusinessException("VALIDATION_ERROR", "Please choose a file");
        }
        if (declaredSize > maxBytes) {
            logReject(purpose, "TOO_LARGE", declaredSize, user);
            throw tooLarge();
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            logReject(purpose, "READ_ERROR", declaredSize, user);
            throw new BusinessException("FILE_ERROR", "Could not read the uploaded file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (bytes.length > maxBytes) {                       // do not trust the declared size alone
            logReject(purpose, "TOO_LARGE", bytes.length, user);
            throw tooLarge();
        }
        String ext = ImageTypeDetector.detect(bytes);        // the file's own bytes decide, not its name or content-type
        if (ext == null) {
            logReject(purpose, "BAD_TYPE", bytes.length, user);
            throw new BusinessException("VALIDATION_ERROR", "Only PNG, JPG and WEBP images are allowed");
        }
        if (ImageDimensions.tooLarge(ImageDimensions.read(bytes))) {
            logReject(purpose, "BAD_DIMENSIONS", bytes.length, user);
            throw new BusinessException("VALIDATION_ERROR",
                    "The image is too large in pixels (maximum " + ImageDimensions.MAX_SIDE + " x " + ImageDimensions.MAX_SIDE + ")");
        }

        FilePaths.Ref ref = FilePaths.create(user.getPartnerId(), purpose.folder(), UUID.randomUUID().toString(), ext);
        Path target = ref.resolve(root).normalize();
        if (!target.startsWith(root)) {                      // cannot happen with the whitelist; kept as a last guard
            logReject(purpose, "PATH", bytes.length, user);
            throw new BusinessException("VALIDATION_ERROR", "Invalid file location");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);   // never overwrites
            restrict(target);
        } catch (IOException e) {
            logReject(purpose, "WRITE_ERROR", bytes.length, user);
            throw new BusinessException("FILE_ERROR", "Could not save the file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("[FILE] upload ok purpose={} size={} userId={} partnerId={} fileId={}",
                purpose, bytes.length, user.getUserId(), user.getPartnerId(), ref.id());
        return new FileUploadResponseDto(ref.logicalPath(), ref.downloadPath(), bytes.length, ImageTypeDetector.contentType(ext));
    }

    // ---------------------------------------------------------------- download

    @Override
    public StoredFile load(String partnerIdText, String folder, String name) {
        CurrentUser user = TenantContext.get();
        if (user == null) {
            log.warn("[FILE] read denied reason=UNAUTHENTICATED");
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        FilePaths.Ref ref = FilePaths.of(partnerIdText, folder, name).orElse(null);
        if (ref == null) {
            log.warn("[FILE] read rejected reason=BAD_PATH userId={} partnerId={}", user.getUserId(), user.getPartnerId());
            throw notFound();
        }
        boolean own = user.getPartnerId() != null && user.getPartnerId() == ref.partnerId();
        if (!own && !user.isSuperAdmin()) {
            log.warn("[FILE] read denied reason=CROSS_TENANT userId={} partnerId={} filePartnerId={} fileId={}",
                    user.getUserId(), user.getPartnerId(), ref.partnerId(), ref.id());
            throw notFound();                                 // same answer as "does not exist"
        }

        try {
            Path p = ref.resolve(root).normalize();
            if (!p.startsWith(root) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                log.info("[FILE] read miss userId={} partnerId={} fileId={}", user.getUserId(), user.getPartnerId(), ref.id());
                throw notFound();
            }
            Path real = p.toRealPath();                       // a symlinked folder must not lead out of the storage root
            if (!real.startsWith(root.toRealPath())) {
                log.warn("[FILE] read rejected reason=OUTSIDE_ROOT userId={} partnerId={} fileId={}",
                        user.getUserId(), user.getPartnerId(), ref.id());
                throw notFound();
            }
            if (Files.size(real) > maxBytes) {
                log.warn("[FILE] read rejected reason=TOO_LARGE userId={} partnerId={} fileId={}",
                        user.getUserId(), user.getPartnerId(), ref.id());
                throw notFound();
            }
            byte[] bytes = Files.readAllBytes(real);
            String ext = ImageTypeDetector.detect(bytes);
            if (ext == null || !ext.equals(ref.ext())) {      // never serve something that is not the image it claims to be
                log.warn("[FILE] read rejected reason=CONTENT_MISMATCH userId={} partnerId={} fileId={}",
                        user.getUserId(), user.getPartnerId(), ref.id());
                throw notFound();
            }
            log.debug("[FILE] read ok userId={} partnerId={} fileId={} size={}",
                    user.getUserId(), user.getPartnerId(), ref.id(), bytes.length);
            return new StoredFile(bytes, ImageTypeDetector.contentType(ext), ref.name());
        } catch (IOException e) {
            log.error("[FILE] read failed reason=IO_ERROR userId={} partnerId={} fileId={}",
                    user.getUserId(), user.getPartnerId(), ref.id());
            throw new BusinessException("FILE_ERROR", "Could not read the file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ----------------------------------------------------------------- helpers

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("File not found");
    }

    private BusinessException tooLarge() {
        return new BusinessException("FILE_TOO_LARGE",
                "The image is too large (maximum " + humanSize(maxBytes) + ")", HttpStatus.CONTENT_TOO_LARGE);
    }

    static String humanSize(long bytes) {
        return bytes % (1024 * 1024) == 0 ? (bytes / (1024 * 1024)) + " MB" : (bytes / 1024) + " KB";
    }

    private static FilePurpose parsePurpose(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return FilePurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isAllowed(FilePurpose purpose, CurrentUser user) {
        Set<String> granted = permissionCache.permissionsForRole(user.getRoleId());
        return switch (purpose) {
            case LOGO -> granted.contains("SETTINGS:EDIT");
            case USER_PHOTO -> granted.contains("USER:CREATE") || granted.contains("USER:EDIT");
        };
    }

    /** Owner read/write only. Ignored where the file system has no POSIX permissions (Windows). */
    private static void restrict(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // best effort
        }
    }

    /** purpose is a fixed enum name or "-"; reason is one of our own codes: nothing the client typed reaches the log. */
    private void logReject(FilePurpose purpose, String reason, long size, CurrentUser user) {
        log.warn("[FILE] upload rejected purpose={} reason={} size={} userId={} partnerId={}",
                purpose == null ? "-" : purpose, reason, size,
                user == null ? null : user.getUserId(), user == null ? null : user.getPartnerId());
    }
}
