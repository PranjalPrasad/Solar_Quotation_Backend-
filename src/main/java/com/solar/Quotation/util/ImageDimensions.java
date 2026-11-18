package com.solar.Quotation.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * Reads only the width and height from the image HEADER (never decodes the pixels), so a tiny file that claims to be
 * 100000 x 100000 pixels ("decompression bomb") is refused before anything - the e-mail logo scaler, the PDF
 * exporter - tries to decode it. Plain JDK. Uses an in-memory stream: no temp files.
 */
public final class ImageDimensions {

    public static final int MAX_SIDE = 8000;
    public static final long MAX_PIXELS = 25_000_000L;

    private ImageDimensions() { }

    /** {width, height}, or null when no reader understands the bytes or the header cannot be read. */
    public static int[] read(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean tooLarge(int[] wh) {
        if (wh == null) return false;
        return wh[0] <= 0 || wh[1] <= 0 || wh[0] > MAX_SIDE || wh[1] > MAX_SIDE
                || (long) wh[0] * wh[1] > MAX_PIXELS;
    }
}
