CREATE TABLE claim_document (
    id            UUID          PRIMARY KEY,
    claim_id      UUID          NOT NULL REFERENCES claim (id),
    category      VARCHAR(30)   NOT NULL,
    file_name     VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL CHECK (size_bytes > 0),
    sha256        VARCHAR(64)   NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    storage_key   VARCHAR(200)  NOT NULL UNIQUE,
    uploaded_by   VARCHAR(50)   NOT NULL,
    uploaded_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_claim_document_category CHECK (category IN
        ('PHOTO', 'POLICE_REPORT', 'REPAIR_QUOTE', 'INVOICE', 'PROOF_OF_OWNERSHIP', 'ADJUSTER_REPORT', 'OTHER')),
    CONSTRAINT ck_claim_document_content_type CHECK (content_type IN
        ('application/pdf', 'image/jpeg', 'image/png', 'image/webp'))
);

CREATE INDEX ix_claim_document_claim ON claim_document (claim_id, uploaded_at);
