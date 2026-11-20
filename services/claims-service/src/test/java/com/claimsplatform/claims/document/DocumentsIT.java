package com.claimsplatform.claims.document;

import com.claimsplatform.claims.TestcontainersConfiguration;
import com.claimsplatform.claims.claim.application.ClaimNotFoundException;
import com.claimsplatform.claims.claim.application.ClaimService;
import com.claimsplatform.claims.claim.domain.ClaimDetails;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.security.Role;
import com.claimsplatform.claims.document.application.DocumentService;
import com.claimsplatform.claims.document.domain.ClaimDocument;
import com.claimsplatform.claims.document.domain.DocumentCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.security.demo-users.password=test-password")
@Import(TestcontainersConfiguration.class)
class DocumentsIT {

    @TempDir
    static Path storage;

    private static final byte[] PDF = "%PDF-1.7 workshop quote".getBytes(StandardCharsets.US_ASCII);
    private static final CurrentUser OWNER = new CurrentUser("claimant1", Role.CLAIMANT, null);
    private static final CurrentUser OTHER_CLAIMANT = new CurrentUser("claimant2", Role.CLAIMANT, null);

    @Autowired
    private ClaimService claimService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storagePath(DynamicPropertyRegistry registry) {
        registry.add("app.documents.storage-path", storage::toString);
    }

    private UUID submitClaim() {
        return claimService.submit(OWNER, new ClaimDetails("POL-SG-7781", Market.SG, ClaimType.MOTOR,
                LocalDate.of(2026, 11, 2), "Side mirror broken", new BigDecimal("900.00")), null).claim().id();
    }

    @Test
    void aDocumentIsStoredListedAndReturnedByteForByte() {
        UUID claimId = submitClaim();

        ClaimDocument stored = documentService.upload(claimId, OWNER, DocumentCategory.REPAIR_QUOTE, "quote.pdf", PDF)
                .document();

        assertThat(documentService.list(claimId, OWNER)).extracting(listed -> listed.document().getId())
                .containsExactly(stored.getId());
        assertThat(documentService.download(claimId, stored.getId(), OWNER).bytes()).isEqualTo(PDF);
        assertThat(storage.resolve(claimId.toString()).resolve(stored.getId().toString())).exists();
    }

    @Test
    void anotherClaimantCannotSeeOrAddDocuments() {
        UUID claimId = submitClaim();
        ClaimDocument stored = documentService.upload(claimId, OWNER, DocumentCategory.PHOTO, "a.pdf", PDF).document();

        assertThatThrownBy(() -> documentService.list(claimId, OTHER_CLAIMANT))
                .isInstanceOf(ClaimNotFoundException.class);
        assertThatThrownBy(() -> documentService.download(claimId, stored.getId(), OTHER_CLAIMANT))
                .isInstanceOf(ClaimNotFoundException.class);
        assertThatThrownBy(() -> documentService.upload(claimId, OTHER_CLAIMANT, DocumentCategory.PHOTO, "b.pdf", PDF))
                .isInstanceOf(ClaimNotFoundException.class);
    }

    @Test
    void aRemovedDocumentLeavesNoRecordAndNoFile() {
        UUID claimId = submitClaim();
        ClaimDocument stored = documentService.upload(claimId, OWNER, DocumentCategory.PHOTO, "dup.pdf", PDF).document();

        documentService.remove(claimId, stored.getId(), OWNER);

        assertThat(documentService.list(claimId, OWNER)).isEmpty();
        assertThat(storage.resolve(claimId.toString()).resolve(stored.getId().toString())).doesNotExist();
    }

    @Test
    void theDatabaseRejectsUnknownCategoriesAndContentTypes() {
        UUID claimId = submitClaim();
        String insert = "INSERT INTO claim_document (id, claim_id, category, file_name, content_type, size_bytes, "
                + "sha256, storage_key, uploaded_by, uploaded_at) VALUES (?, ?, ?, 'x', ?, 1, ?, ?, 'claimant1', now())";

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), claimId, "SELFIE", "application/pdf",
                "a".repeat(64), "k1")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), claimId, "PHOTO", "text/html",
                "a".repeat(64), "k2")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
