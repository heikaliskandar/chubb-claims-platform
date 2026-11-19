package com.chubb.claims.claim.infrastructure;

import com.chubb.claims.claim.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumberSequence();
}
