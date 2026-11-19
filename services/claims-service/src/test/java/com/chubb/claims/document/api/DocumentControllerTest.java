package com.chubb.claims.document.api;

import com.chubb.claims.common.config.SecurityConfig;
import com.chubb.claims.common.security.CurrentUser;
import com.chubb.claims.document.application.DocumentContent;
import com.chubb.claims.document.application.DocumentNotFoundException;
import com.chubb.claims.document.application.DocumentService;
import com.chubb.claims.document.application.UnsupportedDocumentTypeException;
import com.chubb.claims.document.domain.ClaimDocument;
import com.chubb.claims.document.domain.DocumentCategory;
import com.chubb.claims.document.domain.DocumentType;
import com.chubb.claims.employee.application.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentController.class, properties = "app.security.demo-users.password=test-password")
@Import(SecurityConfig.class)
class DocumentControllerTest {

    private static final UUID CLAIM_ID = UUID.fromString("7b1f6a3e-2c4d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID DOCUMENT_ID = UUID.fromString("0f8e7d6c-5b4a-4392-8180-7f6e5d4c3b2a");
    private static final byte[] PDF = "%PDF-1.7 quote".getBytes(StandardCharsets.US_ASCII);
    private static final String BASE = "/api/v1/claims/" + CLAIM_ID + "/documents";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private EmployeeService employeeService;

    private static ClaimDocument document() {
        return new ClaimDocument(DOCUMENT_ID, CLAIM_ID, DocumentCategory.REPAIR_QUOTE, "quote ñ.pdf",
                DocumentType.PDF, PDF.length, "a".repeat(64), CLAIM_ID + "/" + DOCUMENT_ID, "claimant1",
                Instant.parse("2026-11-20T09:00:00Z"));
    }

    private static CurrentUser user(String username) {
        return argThat(user -> user != null && user.username().equals(username));
    }

    @Test
    void uploadsAMultipartFileAndPointsToItsContent() throws Exception {
        given(documentService.upload(eq(CLAIM_ID), user("claimant1"), eq(DocumentCategory.REPAIR_QUOTE),
                eq("quote.pdf"), any())).willReturn(document());

        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "quote.pdf", "application/pdf", PDF))
                        .param("category", "REPAIR_QUOTE")
                        .with(httpBasic("claimant1", "test-password")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/" + DOCUMENT_ID + "/content")))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void anUnknownCategoryOrMissingFileIsABadRequest() throws Exception {
        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "quote.pdf", "application/pdf", PDF))
                        .param("category", "SELFIE")
                        .with(httpBasic("claimant1", "test-password")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(BASE).param("category", "PHOTO").with(httpBasic("claimant1", "test-password")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documentService);
    }

    @Test
    void aFileOfTheWrongTypeIsUnsupportedMediaType() throws Exception {
        given(documentService.upload(any(), any(), any(), anyString(), any()))
                .willThrow(new UnsupportedDocumentTypeException());

        mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "photo.png", "image/png", "<html>".getBytes()))
                        .param("category", "PHOTO")
                        .with(httpBasic("claimant1", "test-password")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void downloadsAsAnUncachedSandboxedAttachment() throws Exception {
        given(documentService.download(eq(CLAIM_ID), eq(DOCUMENT_ID), user("officer1")))
                .willReturn(new DocumentContent(document(), PDF));

        mvc.perform(get(BASE + "/" + DOCUMENT_ID + "/content").with(httpBasic("officer1", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''quote%20")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
    }

    @Test
    void listsDocumentsAndHidesInternalStorageDetails() throws Exception {
        given(documentService.list(eq(CLAIM_ID), user("claimant1"))).willReturn(List.of(document()));

        mvc.perform(get(BASE).with(httpBasic("claimant1", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("quote ñ.pdf"))
                .andExpect(jsonPath("$[0].category").value("REPAIR_QUOTE"))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
    }

    @Test
    void aMissingDocumentIsNotFoundAndAnonymousCallersAreRejected() throws Exception {
        given(documentService.download(any(), any(), any())).willThrow(new DocumentNotFoundException(DOCUMENT_ID));

        mvc.perform(get(BASE + "/" + DOCUMENT_ID + "/content").with(httpBasic("claimant1", "test-password")))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
}
