package org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.util.TimeProvider;

import java.io.Serializable;

class CachedRevisionEntry implements Serializable {
    public String encodedRevisionId;
    public long createTimestamp;

    CachedRevisionEntry(ModuleRevisionId revisionId, TimeProvider timeProvider) {
        this.encodedRevisionId = revisionId.encodeToString();
        this.createTimestamp = timeProvider.getCurrentTime();
    }
}
