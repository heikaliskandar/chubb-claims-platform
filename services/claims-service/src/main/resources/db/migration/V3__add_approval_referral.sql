ALTER TABLE claim
    ADD COLUMN referred_by      VARCHAR(50),
    ADD COLUMN referred_amount  NUMERIC(19, 2) CHECK (referred_amount > 0),
    ADD COLUMN referral_reason  VARCHAR(1000),
    ADD COLUMN referred_at      TIMESTAMPTZ;
