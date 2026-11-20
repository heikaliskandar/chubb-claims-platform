package com.claimsplatform.claims.document.application;

import com.claimsplatform.claims.document.domain.ClaimDocument;

public record DocumentContent(ClaimDocument document, byte[] bytes) {
}
