package com.chubb.claims.document.api.dto;

import com.chubb.claims.document.domain.ClaimDocument;
import com.chubb.claims.document.domain.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A supporting document attached to a claim. The content is downloaded separately.")
public record DocumentResponse(
        UUID id,
        DocumentCategory category,
        @Schema(description = "Original file name, cleaned of path segments and control characters.")
        String fileName,
        @Schema(description = "Detected from the file's content, not from its name.", example = "application/pdf")
        String contentType,
        long sizeBytes,
        @Schema(description = "SHA-256 of the content, for integrity checks.")
        String sha256,
        String uploadedBy,
        Instant uploadedAt) {

    public static DocumentResponse from(ClaimDocument document) {
        return new DocumentResponse(document.getId(), document.getCategory(), document.getFileName(),
                document.getContentType(), document.getSizeBytes(), document.getSha256(), document.getUploadedBy(),
                document.getUploadedAt());
    }
}
