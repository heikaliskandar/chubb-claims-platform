package com.claimsplatform.claims.claim.domain;

public class InvalidClaimStateException extends RuntimeException {

    public InvalidClaimStateException(String message) {
        super(message);
    }
}
