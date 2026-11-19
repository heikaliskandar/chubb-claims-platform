package com.chubb.claims.claim.infrastructure;

import com.chubb.claims.claim.domain.Claim;
import com.chubb.claims.claim.domain.ClaimStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    public static Specification<Claim> matching(ClaimStatus status, String assignedOfficerId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (assignedOfficerId != null) {
                predicates.add(builder.equal(root.get("assignedOfficerId"), assignedOfficerId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
