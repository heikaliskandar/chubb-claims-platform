package com.chubb.claims.document.infrastructure;

public interface DocumentStorage {

    void store(String key, byte[] content);

    byte[] read(String key);

    void delete(String key);
}
