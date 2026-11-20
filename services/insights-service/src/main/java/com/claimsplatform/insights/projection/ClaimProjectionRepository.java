package com.claimsplatform.insights.projection;

import com.claimsplatform.insights.projection.ClaimEvent.ClaimSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class ClaimProjectionRepository {

    private final JdbcClient jdbc;

    public ClaimProjectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean upsertIfNewer(ClaimSnapshot claim, Instant occurredAt) {
        return jdbc.sql("""
                        INSERT INTO claim_projection (
                            claim_id, version, claim_number, claimant_id, market, claim_type, status, currency,
                            claimed_amount, reserve_amount, assigned_officer_id, submitted_at, decided_at, last_event_at)
                        VALUES (
                            :claimId, :version, :claimNumber, :claimantId, :market, :claimType, :status, :currency,
                            :claimedAmount, :reserveAmount, :assignedOfficerId, :submittedAt, :decidedAt, :lastEventAt)
                        ON CONFLICT (claim_id) DO UPDATE SET
                            version = EXCLUDED.version,
                            status = EXCLUDED.status,
                            reserve_amount = EXCLUDED.reserve_amount,
                            assigned_officer_id = EXCLUDED.assigned_officer_id,
                            decided_at = EXCLUDED.decided_at,
                            last_event_at = EXCLUDED.last_event_at
                        WHERE claim_projection.version < EXCLUDED.version
                        """)
                .param("claimId", claim.id())
                .param("version", claim.version())
                .param("claimNumber", claim.claimNumber())
                .param("claimantId", claim.claimantId())
                .param("market", claim.market())
                .param("claimType", claim.claimType())
                .param("status", claim.status())
                .param("currency", claim.currency())
                .param("claimedAmount", claim.claimedAmount())
                .param("reserveAmount", claim.reserveAmount())
                .param("assignedOfficerId", claim.assignedOfficerId())
                .param("submittedAt", utc(claim.submittedAt()))
                .param("decidedAt", utc(claim.decidedAt()))
                .param("lastEventAt", utc(occurredAt))
                .update() == 1;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
