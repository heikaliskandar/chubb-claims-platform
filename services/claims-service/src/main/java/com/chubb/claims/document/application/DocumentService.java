package com.chubb.claims.document.application;

import com.chubb.claims.claim.application.ClaimService;
import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimAccessDeniedException;
import com.chubb.claims.claim.domain.ClaimRuleViolationException;
import com.chubb.claims.common.audit.AccessAudit;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.document.domain.ClaimDocument;
import com.chubb.claims.document.domain.DocumentCategory;
import com.chubb.claims.document.domain.DocumentType;
import com.chubb.claims.document.infrastructure.ClaimDocumentRepository;
import com.chubb.claims.document.infrastructure.DocumentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final ClaimService claims;
    private final ClaimDocumentRepository documents;
    private final DocumentStorage storage;
    private final Clock clock;
    private final int maxPerClaim;

    public DocumentService(ClaimService claims, ClaimDocumentRepository documents, DocumentStorage storage,
                           Clock clock, @Value("${app.documents.max-per-claim}") int maxPerClaim) {
        this.claims = claims;
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
        this.maxPerClaim = maxPerClaim;
    }

    @Transactional
    public ClaimDocument upload(UUID claimId, CurrentUser user, DocumentCategory category, String originalName,
                                byte[] content) {
        Claim claim = claims.findVisibleClaim(claimId, user);
        if (!claim.acceptsDocumentsFrom(user.username())) {
            throw new ClaimAccessDeniedException(
                    "Only the claimant and the assigned officer can add documents, and only while the claim is open");
        }
        if (content.length == 0) {
            throw new ClaimRuleViolationException("The file is empty");
        }
        if (documents.countByClaimId(claimId) >= maxPerClaim) {
            throw new ClaimRuleViolationException("A claim can have at most " + maxPerClaim + " documents");
        }
        DocumentType type = DocumentType.detect(content).orElseThrow(UnsupportedDocumentTypeException::new);

        UUID documentId = UUID.randomUUID();
        String storageKey = claimId + "/" + documentId;
        ClaimDocument document = new ClaimDocument(documentId, claimId, category, safeFileName(originalName, type),
                type, content.length, sha256(content), storageKey, user.username(), clock.instant());

        storage.store(storageKey, content);
        try {
            documents.saveAndFlush(document);
        } catch (RuntimeException exception) {
            storage.delete(storageKey);
            throw exception;
        }
        AccessAudit.changed(user, "claim-document", documentId, "upload claim=" + claimId);
        return document;
    }

    @Transactional(readOnly = true)
    public List<ClaimDocument> list(UUID claimId, CurrentUser user) {
        claims.findVisibleClaim(claimId, user);
        return documents.findByClaimIdOrderByUploadedAt(claimId);
    }

    @Transactional(readOnly = true)
    public DocumentContent download(UUID claimId, UUID documentId, CurrentUser user) {
        claims.findVisibleClaim(claimId, user);
        ClaimDocument document = documents.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        AccessAudit.viewed(user, "claim-document", documentId);
        return new DocumentContent(document, storage.read(document.getStorageKey()));
    }

    static String safeFileName(String originalName, DocumentType type) {
        String name = originalName == null ? "" : originalName;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (name.isEmpty() || name.chars().allMatch(character -> character == '.')) {
            name = "document." + type.name().toLowerCase();
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
