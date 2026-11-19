CREATE TABLE processed_event (
    event_id      UUID         PRIMARY KEY,
    processed_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE claim_projection (
    claim_id             UUID           PRIMARY KEY,
    version              BIGINT         NOT NULL,
    claim_number         VARCHAR(30)    NOT NULL,
    claimant_id          VARCHAR(50)    NOT NULL,
    market               VARCHAR(2)     NOT NULL,
    claim_type           VARCHAR(20)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    claimed_amount       NUMERIC(19, 2) NOT NULL,
    reserve_amount       NUMERIC(19, 2) NOT NULL,
    assigned_officer_id  VARCHAR(50),
    submitted_at         TIMESTAMPTZ    NOT NULL,
    decided_at           TIMESTAMPTZ,
    last_event_at        TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_claim_projection_officer_status ON claim_projection (assigned_officer_id, status);
