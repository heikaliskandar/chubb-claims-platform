package com.claimsplatform.claims.claim.domain;

public class ClaimAccessDeniedException extends RuntimeException {

    public ClaimAccessDeniedException(String message) {
        super(message);
    }
}
