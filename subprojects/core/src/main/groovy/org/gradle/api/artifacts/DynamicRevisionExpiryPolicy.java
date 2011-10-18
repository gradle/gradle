package org.gradle.api.artifacts;

import org.apache.ivy.core.module.id.ModuleRevisionId;

public interface DynamicRevisionExpiryPolicy {
    boolean isExpired(ModuleRevisionId revisionId, long ageMillis);
}
