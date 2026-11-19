package com.chubb.claims.claim.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static com.chubb.claims.claim.domain.ClaimStatus.REJECTED;
import static com.chubb.claims.claim.domain.ClaimStatus.SETTLED;
import static com.chubb.claims.claim.domain.ClaimStatus.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
            "SUBMITTED -> ASSIGNED",
            "SUBMITTED -> WITHDRAWN",
            "ASSIGNED -> UNDER_REVIEW",
            "UNDER_REVIEW -> INFO_REQUESTED",
            "INFO_REQUESTED -> UNDER_REVIEW",
            "UNDER_REVIEW -> APPROVED",
            "UNDER_REVIEW -> REJECTED",
            "UNDER_REVIEW -> PENDING_APPROVAL",
            "PENDING_APPROVAL -> APPROVED",
            "PENDING_APPROVAL -> REJECTED",
            "PENDING_APPROVAL -> UNDER_REVIEW",
            "APPROVED -> SETTLED");

    static Stream<Arguments> everyPairOfStatuses() {
        return Arrays.stream(ClaimStatus.values())
                .flatMap(from -> Arrays.stream(ClaimStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPairOfStatuses")
    void allowsExactlyTheTransitionsInTheStateDiagram(ClaimStatus from, ClaimStatus to) {
        boolean expected = ALLOWED_TRANSITIONS.contains(from + " -> " + to);

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    void onlyRejectedSettledAndWithdrawnAreTerminal() {
        assertThat(Arrays.stream(ClaimStatus.values()).filter(ClaimStatus::isTerminal))
                .containsExactlyInAnyOrder(REJECTED, SETTLED, WITHDRAWN);
    }
}
