package com.claimsplatform.claims.document.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemDocumentStorageTest {

    @TempDir
    Path root;

    @Test
    void storesReadsAndDeletes() {
        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(root);

        storage.store("claim-1/doc-1", new byte[] {1, 2, 3});

        assertThat(storage.read("claim-1/doc-1")).containsExactly(1, 2, 3);
        storage.delete("claim-1/doc-1");
        assertThat(Files.exists(root.resolve("claim-1/doc-1"))).isFalse();
    }

    @Test
    void refusesKeysThatEscapeTheStorageRoot() {
        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(root.resolve("documents"));

        assertThatThrownBy(() -> storage.store("../outside", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read("claim-1/../../outside"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(root.resolve("outside"))).isFalse();
    }

    @Test
    void leavesNoPartialFileBehind() throws Exception {
        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(root);

        storage.store("claim-1/doc-1", new byte[] {9});

        try (var files = Files.list(root.resolve("claim-1"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("doc-1");
        }
    }
}
