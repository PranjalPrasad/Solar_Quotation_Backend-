package com.solar.Quotation.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageTypeDetectorTest {

    private static byte[] padded(int... head) {
        byte[] b = new byte[32];
        for (int i = 0; i < head.length; i++) {
            b[i] = (byte) head[i];
        }
        return b;
    }

    @Test
    void detectsPng() {
        assertEquals("png", ImageTypeDetector.detect(padded(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)));
    }

    @Test
    void detectsJpeg() {
        assertEquals("jpg", ImageTypeDetector.detect(padded(0xFF, 0xD8, 0xFF, 0xE0)));
    }

    @Test
    void detectsWebp() {
        byte[] b = padded('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
        assertEquals("webp", ImageTypeDetector.detect(b));
    }

    @Test
    void rejectsScriptsSvgAndShortInput() {
        assertNull(ImageTypeDetector.detect("<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageTypeDetector.detect("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageTypeDetector.detect(new byte[]{1, 2, 3}));
        assertNull(ImageTypeDetector.detect(null));
    }

    @Test
    void contentTypes() {
        assertEquals("image/png", ImageTypeDetector.contentType("png"));
        assertEquals("image/jpeg", ImageTypeDetector.contentType("jpg"));
        assertEquals("image/webp", ImageTypeDetector.contentType("webp"));
    }
}
