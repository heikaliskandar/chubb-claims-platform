package com.chubb.claims.document.application;

import com.chubb.claims.document.domain.ClaimDocument;

public record DocumentContent(ClaimDocument document, byte[] bytes) {
}
