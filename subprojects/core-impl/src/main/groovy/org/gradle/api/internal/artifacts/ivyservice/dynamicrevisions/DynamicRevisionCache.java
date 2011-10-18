package org.gradle.api.internal.artifacts.ivyservice.dynamicrevisions;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;

public interface DynamicRevisionCache {
    void saveResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision, ModuleRevisionId resolvedRevision);
    CachedRevision getResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision);
    
    interface CachedRevision {
        ModuleRevisionId getRevision();
        long getAgeMillis();
    }
}
