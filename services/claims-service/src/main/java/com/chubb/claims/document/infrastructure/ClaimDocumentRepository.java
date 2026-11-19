package com.chubb.claims.document.infrastructure;

import com.chubb.claims.document.domain.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    List<ClaimDocument> findByClaimIdOrderByUploadedAt(UUID claimId);

    Optional<ClaimDocument> findByIdAndClaimId(UUID id, UUID claimId);

    long countByClaimId(UUID claimId);
}
