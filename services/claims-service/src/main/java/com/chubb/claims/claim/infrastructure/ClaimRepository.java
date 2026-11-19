package com.chubb.claims.claim.infrastructure;

import com.chubb.claims.claim.domain.Claim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimantIdAndIdempotencyKey(String claimantId, String idempotencyKey);

    Page<Claim> findByClaimantId(String claimantId, Pageable pageable);

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumberSequence();
}
