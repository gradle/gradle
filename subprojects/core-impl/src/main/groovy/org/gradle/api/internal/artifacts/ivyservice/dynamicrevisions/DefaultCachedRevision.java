package org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.util.TimeProvider;

import java.io.Serializable;

class DefaultCachedRevision implements DynamicRevisionCache.CachedRevision, Serializable {
    private final ModuleRevisionId revision;
    private final long ageMillis;

    public DefaultCachedRevision(CachedRevisionEntry entry, TimeProvider timeProvider) {
        revision = ModuleRevisionId.decode(entry.encodedRevisionId);
        ageMillis = timeProvider.getCurrentTime() - entry.createTimestamp;
    }

    public ModuleRevisionId getRevision() {
        return revision;
    }

    public long getAgeMillis() {
        return ageMillis;
    }
}
