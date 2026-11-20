package com.claimsplatform.claims.document.application;

import com.claimsplatform.claims.claim.application.ClaimService;
import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.domain.ClaimAccessDeniedException;
import com.claimsplatform.claims.claim.domain.ClaimDetails;
import com.claimsplatform.claims.claim.domain.ClaimRuleViolationException;
import com.claimsplatform.claims.claim.domain.ClaimType;
import com.claimsplatform.claims.claim.domain.Market;
import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.common.security.Position;
import com.claimsplatform.claims.common.security.Role;
import com.claimsplatform.claims.document.domain.ClaimDocument;
import com.claimsplatform.claims.document.domain.DocumentCategory;
import com.claimsplatform.claims.document.domain.DocumentType;
import com.claimsplatform.claims.document.infrastructure.ClaimDocumentRepository;
import com.claimsplatform.claims.document.infrastructure.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T09:00:00Z");
    private static final byte[] PDF = "%PDF-1.7 quote".getBytes(StandardCharsets.US_ASCII);
    private static final CurrentUser CLAIMANT = new CurrentUser("claimant1", Role.CLAIMANT, null);
    private static final CurrentUser OFFICER = new CurrentUser("officer1", Role.OFFICER, Position.CLAIMS_OFFICER);
    private static final CurrentUser OTHER_OFFICER =
            new CurrentUser("officer2", Role.OFFICER, Position.SENIOR_CLAIMS_OFFICER);

    private final ClaimService claims = mock(ClaimService.class);
    private final ClaimDocumentRepository documents = mock(ClaimDocumentRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final DocumentService service =
            new DocumentService(claims, documents, storage, Clock.fixed(NOW, ZoneOffset.UTC), 3);
    private Claim claim;

    @BeforeEach
    void claimAssignedToOfficer1() {
        claim = Claim.submit("CLM-SG-2026-000001", "claimant1", new ClaimDetails("POL-1", Market.SG,
                ClaimType.MOTOR, LocalDate.of(2026, 11, 1), "Rear-ended", new BigDecimal("3200.00")), null, NOW);
        claim.assignTo("officer1", NOW);
        when(claims.findVisibleClaim(eq(claim.getId()), any())).thenReturn(claim);
    }

    @Test
    void storesTheFileUnderAGeneratedKeyAndRecordsItsDetectedTypeAndHash() {
        ListedDocument listed =
                service.upload(claim.getId(), CLAIMANT, DocumentCategory.REPAIR_QUOTE, "C:\\fakepath\\quote.pdf", PDF);
        ClaimDocument document = listed.document();
        assertThat(listed.removable()).as("the claim is already with an officer").isFalse();

        assertThat(document.getFileName()).isEqualTo("quote.pdf");
        assertThat(document.getContentType()).isEqualTo("application/pdf");
        assertThat(document.getSizeBytes()).isEqualTo(PDF.length);
        assertThat(document.getSha256()).hasSize(64);
        assertThat(document.getStorageKey()).isEqualTo(claim.getId() + "/" + document.getId());
        assertThat(document.getUploadedBy()).isEqualTo("claimant1");
        assertThat(document.getUploadedAt()).isEqualTo(NOW);
        verify(storage).store(document.getStorageKey(), PDF);
        verify(documents).saveAndFlush(document);
    }

    @Test
    void theAssignedOfficerCanUploadButAColleagueCannot() {
        assertThat(service.upload(claim.getId(), OFFICER, DocumentCategory.ADJUSTER_REPORT, "report.pdf", PDF))
                .isNotNull();

        assertThatThrownBy(() -> service.upload(claim.getId(), OTHER_OFFICER, DocumentCategory.OTHER, "x.pdf", PDF))
                .isInstanceOf(ClaimAccessDeniedException.class);
    }

    @Test
    void aClosedClaimAcceptsNoMoreDocuments() {
        claim.startReview("officer1", NOW);
        claim.reject("officer1", "Not covered", NOW);

        assertThatThrownBy(() -> service.upload(claim.getId(), CLAIMANT, DocumentCategory.PHOTO, "a.pdf", PDF))
                .isInstanceOf(ClaimAccessDeniedException.class);
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsFilesThatAreNotReallyPdfsOrImages() {
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.upload(claim.getId(), CLAIMANT, DocumentCategory.PHOTO, "photo.png", html))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        verify(storage, never()).store(anyString(), any());
    }

    @Test
    void rejectsEmptyFilesAndTooManyDocuments() {
        assertThatThrownBy(() -> service.upload(claim.getId(), CLAIMANT, DocumentCategory.PHOTO, "a.pdf", new byte[0]))
                .isInstanceOf(ClaimRuleViolationException.class);

        when(documents.countByClaimId(claim.getId())).thenReturn(3L);
        assertThatThrownBy(() -> service.upload(claim.getId(), CLAIMANT, DocumentCategory.PHOTO, "a.pdf", PDF))
                .isInstanceOf(ClaimRuleViolationException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void removesTheStoredFileWhenTheRecordCannotBeSaved() {
        when(documents.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.upload(claim.getId(), CLAIMANT, DocumentCategory.PHOTO, "a.pdf", PDF))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(storage).delete(anyString());
    }

    @Test
    void aDocumentFromAnotherClaimIsNotFound() {
        UUID documentId = UUID.randomUUID();
        when(documents.findByIdAndClaimId(documentId, claim.getId())).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.download(claim.getId(), documentId, CLAIMANT))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void theClaimantCanRemoveTheirOwnDocumentUntilTheClaimIsPickedUp() {
        Claim fresh = Claim.submit("CLM-SG-2026-000002", "claimant1", new ClaimDetails("POL-2", Market.SG,
                ClaimType.MOTOR, LocalDate.of(2026, 11, 1), "Mirror", new BigDecimal("300.00")), null, NOW);
        when(claims.findVisibleClaim(eq(fresh.getId()), any())).thenReturn(fresh);
        ClaimDocument mine = new ClaimDocument(UUID.randomUUID(), fresh.getId(), DocumentCategory.PHOTO, "dup.pdf",
                DocumentType.PDF, 10, "a".repeat(64), fresh.getId() + "/x", "claimant1", NOW);
        when(documents.findByIdAndClaimId(mine.getId(), fresh.getId())).thenReturn(java.util.Optional.of(mine));

        service.remove(fresh.getId(), mine.getId(), CLAIMANT);

        verify(documents).delete(mine);
        verify(storage).delete(mine.getStorageKey());

        fresh.assignTo("officer1", NOW);
        assertThatThrownBy(() -> service.remove(fresh.getId(), mine.getId(), CLAIMANT))
                .isInstanceOf(ClaimAccessDeniedException.class);
    }

    @Test
    void nobodyCanRemoveSomeoneElsesDocument() {
        Claim fresh = Claim.submit("CLM-SG-2026-000003", "claimant1", new ClaimDetails("POL-3", Market.SG,
                ClaimType.MOTOR, LocalDate.of(2026, 11, 1), "Mirror", new BigDecimal("300.00")), null, NOW);
        when(claims.findVisibleClaim(eq(fresh.getId()), any())).thenReturn(fresh);
        ClaimDocument officers = new ClaimDocument(UUID.randomUUID(), fresh.getId(), DocumentCategory.OTHER, "x.pdf",
                DocumentType.PDF, 10, "a".repeat(64), fresh.getId() + "/y", "officer1", NOW);
        when(documents.findByIdAndClaimId(officers.getId(), fresh.getId())).thenReturn(java.util.Optional.of(officers));

        assertThatThrownBy(() -> service.remove(fresh.getId(), officers.getId(), CLAIMANT))
                .isInstanceOf(ClaimAccessDeniedException.class);
        verify(documents, never()).delete(any());
    }

    @Test
    void cleansFileNames() {
        assertThat(DocumentService.safeFileName("../../etc/passwd.pdf", DocumentType.PDF)).isEqualTo("passwd.pdf");
        assertThat(DocumentService.safeFileName("in\u0000voice<1>.pdf", DocumentType.PDF)).isEqualTo("invoice1.pdf");
        assertThat(DocumentService.safeFileName("  ", DocumentType.PNG)).isEqualTo("document.png");
        assertThat(DocumentService.safeFileName(null, DocumentType.JPEG)).isEqualTo("document.jpeg");
        assertThat(DocumentService.safeFileName("..", DocumentType.PDF)).isEqualTo("document.pdf");
        assertThat(DocumentService.safeFileName("a".repeat(300) + ".pdf", DocumentType.PDF)).hasSize(255).endsWith(".pdf");
    }
}
