package com.chubb.claims.document.application;

public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException() {
        super("Only PDF, JPEG, PNG and WebP files are accepted. The file's content was checked, not its name.");
    }
}
