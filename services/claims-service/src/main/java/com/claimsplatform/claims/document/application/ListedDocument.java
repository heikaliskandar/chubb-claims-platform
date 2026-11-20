package com.claimsplatform.claims.document.application;

import com.claimsplatform.claims.document.domain.ClaimDocument;

public record ListedDocument(ClaimDocument document, boolean removable) {
}
