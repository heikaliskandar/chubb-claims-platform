package com.claimsplatform.claims.claim.application;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(UUID claimId) {
        super("Claim " + claimId + " not found");
    }
}
