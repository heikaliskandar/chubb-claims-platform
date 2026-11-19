package com.chubb.claims.claim.domain;

public class InvalidClaimStateException extends RuntimeException {

    public InvalidClaimStateException(String message) {
        super(message);
    }
}
