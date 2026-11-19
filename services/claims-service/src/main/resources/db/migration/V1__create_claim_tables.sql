CREATE SEQUENCE claim_number_seq;

CREATE TABLE claim (
    id                    UUID           PRIMARY KEY,
    claim_number          VARCHAR(30)    NOT NULL UNIQUE,
    policy_number         VARCHAR(50)    NOT NULL,
    claimant_id           VARCHAR(50)    NOT NULL,
    market                VARCHAR(2)     NOT NULL,
    claim_type            VARCHAR(20)    NOT NULL,
    incident_date         DATE           NOT NULL,
    incident_description  VARCHAR(2000)  NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    claimed_amount        NUMERIC(19, 2) NOT NULL CHECK (claimed_amount > 0),
    reserve_amount        NUMERIC(19, 2) NOT NULL CHECK (reserve_amount >= 0),
    status                VARCHAR(20)    NOT NULL,
    assigned_officer_id   VARCHAR(50),
    decision_outcome      VARCHAR(10),
    approved_amount       NUMERIC(19, 2) CHECK (approved_amount > 0),
    decision_reason       VARCHAR(1000),
    decided_by            VARCHAR(50),
    decided_at            TIMESTAMPTZ,
    idempotency_key       VARCHAR(100),
    version               BIGINT         NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    -- Leading claimant_id column also serves "my claims" lookups, so there is no separate claimant index.
    CONSTRAINT uq_claim_claimant_idempotency_key UNIQUE (claimant_id, idempotency_key)
);

CREATE INDEX ix_claim_status ON claim (status);
CREATE INDEX ix_claim_officer_status ON claim (assigned_officer_id, status);

CREATE TABLE claim_status_history (
    id           UUID          PRIMARY KEY,
    claim_id     UUID          NOT NULL REFERENCES claim (id),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20)   NOT NULL,
    changed_by   VARCHAR(50)   NOT NULL,
    reason       VARCHAR(1000),
    changed_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE information_request (
    id            UUID          PRIMARY KEY,
    claim_id      UUID          NOT NULL REFERENCES claim (id),
    requested_by  VARCHAR(50)   NOT NULL,
    question      VARCHAR(2000) NOT NULL,
    response      VARCHAR(4000),
    status        VARCHAR(10)   NOT NULL,
    requested_at  TIMESTAMPTZ   NOT NULL,
    answered_at   TIMESTAMPTZ
);

CREATE TABLE assessment (
    id               UUID           PRIMARY KEY,
    claim_id         UUID           NOT NULL REFERENCES claim (id),
    officer_id       VARCHAR(50)    NOT NULL,
    assessed_amount  NUMERIC(19, 2) NOT NULL CHECK (assessed_amount >= 0),
    currency         VARCHAR(3)     NOT NULL,
    notes            VARCHAR(2000),
    assessed_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_claim_status_history_claim ON claim_status_history (claim_id);
CREATE INDEX ix_information_request_claim ON information_request (claim_id);
CREATE INDEX ix_assessment_claim ON assessment (claim_id);
