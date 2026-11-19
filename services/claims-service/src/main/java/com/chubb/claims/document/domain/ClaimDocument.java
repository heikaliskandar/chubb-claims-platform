package com.chubb.claims.document.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_document")
public class ClaimDocument {

    @Id
    private UUID id;

    private UUID claimId;

    @Enumerated(EnumType.STRING)
    private DocumentCategory category;

    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private String storageKey;
    private String uploadedBy;
    private Instant uploadedAt;

    protected ClaimDocument() {
    }

    public ClaimDocument(UUID id, UUID claimId, DocumentCategory category, String fileName, DocumentType type,
                         long sizeBytes, String sha256, String storageKey, String uploadedBy, Instant uploadedAt) {
        this.id = id;
        this.claimId = claimId;
        this.category = category;
        this.fileName = fileName;
        this.contentType = type.mediaType();
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
