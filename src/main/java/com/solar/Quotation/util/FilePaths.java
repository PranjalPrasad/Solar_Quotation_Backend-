package com.solar.Quotation.util;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ONLY place that understands the layout of stored files (plain JDK, no Spring).
 *
 *   disk:     {storage-dir}/{partnerId}/{folder}/{uuid}.{ext}
 *   logical:  /files/{partnerId}/{folder}/{uuid}.{ext}        (what logoPath / photoPath hold in the database)
 *   download: /api/files/{partnerId}/{folder}/{uuid}.{ext}    (authenticated, tenant-checked)
 *
 * Every name is matched against a strict whitelist pattern, so ".." , "/" , "\" , "%2e", NUL bytes, other
 * extensions and anything that is not a lower-case UUID can never become a path.
 */
public final class FilePaths {

    public static final String LOGICAL_PREFIX = "/files/";
    public static final String DOWNLOAD_PREFIX = "/api/files/";

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern PARTNER = Pattern.compile("^[1-9][0-9]{0,17}$");
    private static final Pattern FOLDER = Pattern.compile("^(branding|users)$");
    private static final Pattern NAME = Pattern.compile("^(" + UUID_RE + ")\\.(png|jpg|webp)$");
    private static final Pattern LOGICAL = Pattern.compile(
            "^/files/([1-9][0-9]{0,17})/(branding|users)/(" + UUID_RE + ")\\.(png|jpg|webp)$");

    private FilePaths() { }

    /** A validated stored-file reference. Cannot be built from unchecked text except through parse()/of(). */
    public record Ref(long partnerId, String folder, String id, String ext) {
        public String name() { return id + "." + ext; }
        public String logicalPath() { return LOGICAL_PREFIX + partnerId + "/" + folder + "/" + name(); }
        public String downloadPath() { return DOWNLOAD_PREFIX + partnerId + "/" + folder + "/" + name(); }
        public Path resolve(Path root) {
            return root.resolve(String.valueOf(partnerId)).resolve(folder).resolve(name());
        }
    }

    /** Builds a reference for a file the server itself is about to create. */
    public static Ref create(long partnerId, String folder, String uuid, String ext) {
        return of(String.valueOf(partnerId), folder, uuid + "." + ext)
                .orElseThrow(() -> new IllegalArgumentException("Invalid file reference parts"));
    }

    /** Parses the value stored in the database (/files/...). Empty when it is not exactly that shape. */
    public static Optional<Ref> parse(String logicalPath) {
        if (logicalPath == null) return Optional.empty();
        Matcher m = LOGICAL.matcher(logicalPath);
        if (!m.matches()) return Optional.empty();
        return Optional.of(new Ref(Long.parseLong(m.group(1)), m.group(2), m.group(3), m.group(4)));
    }

    /** Parses the three URL path segments of the download endpoint. */
    public static Optional<Ref> of(String partnerId, String folder, String name) {
        if (partnerId == null || folder == null || name == null) return Optional.empty();
        if (!PARTNER.matcher(partnerId).matches() || !FOLDER.matcher(folder).matches()) return Optional.empty();
        Matcher n = NAME.matcher(name);
        if (!n.matches()) return Optional.empty();
        return Optional.of(new Ref(Long.parseLong(partnerId), folder, n.group(1), n.group(2)));
    }

    /**
     * May this value be stored in a record? Blank is fine (no file). Otherwise it must be a well-formed reference
     * that belongs to one of the allowed partners. Callers decide separately whether an unchanged old value is kept.
     */
    public static boolean isReferenceAllowed(String path, Collection<Long> allowedPartnerIds) {
        if (path == null || path.isBlank()) return true;
        Optional<Ref> ref = parse(path.trim());
        return ref.isPresent() && allowedPartnerIds != null && allowedPartnerIds.contains(ref.get().partnerId());
    }
}
