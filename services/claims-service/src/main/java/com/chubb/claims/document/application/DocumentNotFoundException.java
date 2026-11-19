package com.chubb.claims.document.application;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID documentId) {
        super("Document " + documentId + " not found");
    }
}
