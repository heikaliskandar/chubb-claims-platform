package com.claimsplatform.claims.document.domain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTest {

    static final byte[] PDF = "%PDF-1.7\n%âã\n".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};
    static final byte[] WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    void recognisesTheAcceptedFormatsFromTheirContent() {
        assertThat(DocumentType.detect(PDF)).contains(DocumentType.PDF);
        assertThat(DocumentType.detect(JPEG)).contains(DocumentType.JPEG);
        assertThat(DocumentType.detect(PNG)).contains(DocumentType.PNG);
        assertThat(DocumentType.detect(WEBP)).contains(DocumentType.WEBP);
    }

    @Test
    void rejectsContentThatCouldRunInABrowserOrOnAMachine() {
        assertThat(DocumentType.detect("<html><script>alert(1)</script>".getBytes())).isEmpty();
        assertThat(DocumentType.detect("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes())).isEmpty();
        assertThat(DocumentType.detect(new byte[] {'M', 'Z', (byte) 0x90, 0})).isEmpty();
        assertThat(DocumentType.detect("RIFF\u0000\u0000\u0000\u0000WAVE".getBytes(StandardCharsets.ISO_8859_1)))
                .isEmpty();
    }

    @Test
    void rejectsEmptyOrTruncatedContent() {
        assertThat(DocumentType.detect(new byte[0])).isEmpty();
        assertThat(DocumentType.detect(new byte[] {(byte) 0x89, 'P', 'N'})).isEmpty();
        assertThat(DocumentType.detect("%PD".getBytes())).isEmpty();
    }
}
