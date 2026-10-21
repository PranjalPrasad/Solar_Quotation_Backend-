package com.solar.Quotation.util;

public final class ImageTypeDetector {

    private ImageTypeDetector() { }

    /** Returns png, jpg or webp, or null when the bytes are not one of those images. */
    public static String detect(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    public static String contentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
