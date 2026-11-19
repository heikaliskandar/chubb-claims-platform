package com.chubb.insights.dashboard;

import com.chubb.insights.dashboard.ExposureReport.ExposureLine;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DashboardQueries {

    private static final List<String> CLOSED_STATUSES = List.of("REJECTED", "SETTLED");

    private final JdbcClient jdbc;

    public DashboardQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExposureLine> outstandingExposure() {
        return jdbc.sql("""
                        SELECT market, claim_type, currency, COUNT(*) AS open_claims, SUM(reserve_amount) AS reserve
                        FROM claim_projection
                        WHERE status NOT IN (:closed)
                        GROUP BY market, claim_type, currency
                        ORDER BY market, claim_type, currency
                        """)
                .param("closed", CLOSED_STATUSES)
                .query((rs, rowNum) -> new ExposureLine(rs.getString("market"), rs.getString("claim_type"),
                        rs.getString("currency"), rs.getLong("open_claims"), rs.getBigDecimal("reserve")))
                .list();
    }

    public List<WorkloadReport.Row> openClaimsByOfficerAndStatus() {
        return jdbc.sql("""
                        SELECT assigned_officer_id, status, COUNT(*) AS claims
                        FROM claim_projection
                        WHERE status NOT IN (:closed)
                        GROUP BY assigned_officer_id, status
                        """)
                .param("closed", CLOSED_STATUSES)
                .query((rs, rowNum) -> new WorkloadReport.Row(rs.getString("assigned_officer_id"),
                        rs.getString("status"), rs.getLong("claims")))
                .list();
    }

    public List<PerformanceReport.Row> decisionsByOfficer() {
        return jdbc.sql("""
                        SELECT assigned_officer_id,
                               COUNT(*) FILTER (WHERE status IN ('APPROVED', 'SETTLED')) AS approved,
                               COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected,
                               COUNT(*) FILTER (WHERE status IN (:closed)) AS closed,
                               CAST(SUM(EXTRACT(EPOCH FROM decided_at - submitted_at)) AS BIGINT) AS seconds_to_decision
                        FROM claim_projection
                        WHERE decided_at IS NOT NULL
                        GROUP BY assigned_officer_id
                        """)
                .param("closed", CLOSED_STATUSES)
                .query((rs, rowNum) -> new PerformanceReport.Row(rs.getString("assigned_officer_id"),
                        rs.getLong("approved"), rs.getLong("rejected"), rs.getLong("closed"),
                        rs.getLong("seconds_to_decision")))
                .list();
    }

    public Instant lastEventAt() {
        return jdbc.sql("SELECT max(last_event_at) FROM claim_projection")
                .query((rs, rowNum) -> Optional.ofNullable(rs.getObject(1, OffsetDateTime.class)))
                .single()
                .map(OffsetDateTime::toInstant)
                .orElse(null);
    }
}
