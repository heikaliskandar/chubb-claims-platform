package com.chubb.claims.document.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public enum DocumentType {
    PDF("application/pdf"),
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_MARKER = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final String mediaType;

    DocumentType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<DocumentType> detect(byte[] content) {
        if (startsWith(content, 0, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARKER)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
