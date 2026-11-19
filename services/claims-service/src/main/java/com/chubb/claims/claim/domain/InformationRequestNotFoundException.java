package com.chubb.claims.claim.domain;

import java.util.UUID;

public class InformationRequestNotFoundException extends RuntimeException {

    public InformationRequestNotFoundException(UUID requestId) {
        super("Information request " + requestId + " not found on this claim");
    }
}
