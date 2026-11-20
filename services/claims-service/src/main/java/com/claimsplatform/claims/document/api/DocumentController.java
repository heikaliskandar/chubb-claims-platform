package com.claimsplatform.claims.document.api;

import com.claimsplatform.claims.common.security.CurrentUser;
import com.claimsplatform.claims.document.api.dto.DocumentResponse;
import com.claimsplatform.claims.document.application.DocumentContent;
import com.claimsplatform.claims.document.application.DocumentService;
import com.claimsplatform.claims.document.application.ListedDocument;
import com.claimsplatform.claims.document.domain.ClaimDocument;
import com.claimsplatform.claims.document.domain.DocumentCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents")
@Tag(name = "Documents", description = "Supporting documents attached to a claim: photos, police reports, quotes, "
        + "invoices. Visible to anyone who can see the claim. The claimant and the assigned officer can add them while "
        + "the claim is open; whoever added a document can remove it until a claims officer picks up the claim.")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    @Operation(summary = "List a claim's documents", description = "Oldest first.")
    @ApiResponse(responseCode = "200", description = "The documents")
    @ApiResponse(responseCode = "404", description = "The claim does not exist or is not visible to the caller",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<DocumentResponse> list(@PathVariable UUID claimId, Authentication authentication) {
        return documents.list(claimId, CurrentUser.from(authentication)).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a document",
            description = "PDF, JPEG, PNG or WebP, up to 10 MB, at most 20 per claim. The type is detected from "
                    + "the file's content; a renamed file of another type is rejected.")
    @ApiResponse(responseCode = "201", description = "Stored")
    @ApiResponse(responseCode = "403", description = "Not the claimant or assigned officer, or the claim is closed",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Larger than 10 MB",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Not a PDF, JPEG, PNG or WebP file",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Empty file, or the claim already has 20 documents",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID claimId,
            @Parameter(description = "The file") @RequestPart("file") MultipartFile file,
            @Parameter(description = "What the document is") @RequestParam DocumentCategory category,
            Authentication authentication) throws IOException {
        ListedDocument listed = documents.upload(claimId, CurrentUser.from(authentication), category,
                file.getOriginalFilename(), file.getBytes());
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}/content").buildAndExpand(listed.document().getId()).toUri())
                .body(DocumentResponse.from(listed));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Remove a document",
            description = "Only whoever added it, and only before a claims officer picks up the claim. After that, "
                    + "documents are part of the claim's evidence and cannot be removed.")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "403", description = "Not added by you, or the claim has been picked up",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> remove(@PathVariable UUID claimId, @PathVariable UUID documentId,
                                       Authentication authentication) {
        documents.remove(claimId, documentId, CurrentUser.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}/content")
    @Operation(summary = "Download a document",
            description = "Sent as an attachment with no-store caching and a sandbox CSP, so it is never cached "
                    + "or run by the browser.")
    @ApiResponse(responseCode = "200", description = "The file",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Not found, or the claim is not visible to the caller",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<byte[]> download(@PathVariable UUID claimId, @PathVariable UUID documentId,
                                           Authentication authentication) {
        DocumentContent content = documents.download(claimId, documentId, CurrentUser.from(authentication));
        ClaimDocument document = content.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(content.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(content.bytes());
    }
}
