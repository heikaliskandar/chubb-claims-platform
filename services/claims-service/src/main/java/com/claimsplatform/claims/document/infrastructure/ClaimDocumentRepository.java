package com.claimsplatform.claims.document.infrastructure;

import com.claimsplatform.claims.document.domain.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    List<ClaimDocument> findByClaimIdOrderByUploadedAt(UUID claimId);

    Optional<ClaimDocument> findByIdAndClaimId(UUID id, UUID claimId);

    long countByClaimId(UUID claimId);
}
