package com.chubb.insights.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ClaimantNotifier {

    private static final Logger log = LoggerFactory.getLogger(ClaimantNotifier.class);

    private static final Map<String, String> MESSAGES = Map.of(
            "INFORMATION_REQUESTED", "We need more information about your claim.",
            "CLAIM_APPROVED", "Your claim has been approved.",
            "CLAIM_REJECTED", "Your claim has been rejected.",
            "CLAIM_SETTLED", "Your claim has been settled.");

    public void notifyClaimant(ClaimEvent event) {
        String message = MESSAGES.get(event.eventType());
        if (message != null) {
            log.info("Notification to {} about claim {}: {}",
                    event.claim().claimantId(), event.claim().claimNumber(), message);
        }
    }
}
