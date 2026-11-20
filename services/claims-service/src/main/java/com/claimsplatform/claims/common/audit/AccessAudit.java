package com.claimsplatform.claims.common.audit;

import com.claimsplatform.claims.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AccessAudit {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private AccessAudit() {
    }

    public static void viewed(CurrentUser actor, String resourceType, Object resourceId) {
        AUDIT.info("action=VIEW actor={} role={} resource={} id={}",
                actor.username(), actor.role(), resourceType, resourceId);
    }

    public static void changed(CurrentUser actor, String resourceType, Object resourceId, String change) {
        AUDIT.info("action=CHANGE actor={} role={} resource={} id={} change={}",
                actor.username(), actor.role(), resourceType, resourceId, change);
    }
}
