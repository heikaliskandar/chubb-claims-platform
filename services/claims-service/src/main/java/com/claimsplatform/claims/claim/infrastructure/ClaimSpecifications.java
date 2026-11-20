package com.claimsplatform.claims.claim.infrastructure;

import com.claimsplatform.claims.claim.domain.Claim;
import com.claimsplatform.claims.claim.domain.ClaimStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ClaimSpecifications {

    private static final List<ClaimStatus> CLOSED = List.of(ClaimStatus.REJECTED, ClaimStatus.SETTLED, ClaimStatus.WITHDRAWN);

    private ClaimSpecifications() {
    }

    public static Specification<Claim> matching(ClaimStatus status, String assignedOfficerId, Boolean closed) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (assignedOfficerId != null) {
                predicates.add(builder.equal(root.get("assignedOfficerId"), assignedOfficerId));
            }
            if (closed != null) {
                predicates.add(closed ? root.get("status").in(CLOSED) : builder.not(root.get("status").in(CLOSED)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Claim> ofClaimant(String claimantId) {
        return (root, query, builder) -> builder.equal(root.get("claimantId"), claimantId);
    }

    public static Specification<Claim> unassignedOrAssignedTo(Collection<String> officerIds) {
        return (root, query, builder) -> builder.or(
                builder.isNull(root.get("assignedOfficerId")),
                root.get("assignedOfficerId").in(officerIds));
    }

    public static Specification<Claim> referredBy(Collection<String> officerIds) {
        return (root, query, builder) -> root.get("referral").get("referredBy").in(officerIds);
    }

    public static Specification<Claim> awaitingApproval(boolean pending) {
        return (root, query, builder) -> pending
                ? builder.equal(root.get("status"), ClaimStatus.PENDING_APPROVAL)
                : builder.notEqual(root.get("status"), ClaimStatus.PENDING_APPROVAL);
    }
}
