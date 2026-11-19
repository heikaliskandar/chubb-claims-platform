ALTER TABLE claim
    ADD CONSTRAINT ck_claim_status CHECK (status IN
        ('SUBMITTED', 'ASSIGNED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SETTLED')),
    ADD CONSTRAINT ck_claim_market CHECK (market IN ('SG', 'MY', 'HK', 'TH', 'ID', 'PH')),
    ADD CONSTRAINT ck_claim_type CHECK (claim_type IN ('MOTOR', 'PROPERTY')),
    ADD CONSTRAINT ck_claim_currency CHECK (currency IN ('SGD', 'MYR', 'HKD', 'THB', 'IDR', 'PHP')),
    ADD CONSTRAINT ck_claim_decision_outcome CHECK (decision_outcome IN ('APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_claim_decision_complete CHECK (
        (decision_outcome IS NULL AND decided_by IS NULL AND decided_at IS NULL)
        OR (decision_outcome IS NOT NULL AND decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    ADD CONSTRAINT ck_claim_referral_complete CHECK (
        (referred_by IS NULL AND referred_amount IS NULL AND referred_at IS NULL)
        OR (referred_by IS NOT NULL AND referred_amount IS NOT NULL AND referred_at IS NOT NULL));

ALTER TABLE information_request
    ADD CONSTRAINT ck_information_request_status CHECK (status IN ('OPEN', 'ANSWERED')),
    ADD CONSTRAINT ck_information_request_answered CHECK (
        (status = 'OPEN' AND response IS NULL AND answered_at IS NULL)
        OR (status = 'ANSWERED' AND response IS NOT NULL AND answered_at IS NOT NULL));

ALTER TABLE employee
    ADD CONSTRAINT ck_employee_position CHECK (position IN
        ('CLAIMS_OFFICER', 'SENIOR_CLAIMS_OFFICER', 'TEAM_LEAD', 'CLAIMS_MANAGER')),
    ADD CONSTRAINT ck_employee_market CHECK (market IN ('SG', 'MY', 'HK', 'TH', 'ID', 'PH')),
    ADD CONSTRAINT ck_employee_employment_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT')),
    ADD CONSTRAINT ck_employee_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'INACTIVE'));

CREATE INDEX ix_claim_referred_by ON claim (referred_by, referred_at DESC) WHERE referred_by IS NOT NULL;
