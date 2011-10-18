package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;

public interface DynamicRevisionCache {
    void saveResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision, ModuleRevisionId resolvedRevision);
    ModuleRevisionId getResolvedRevision(DependencyResolver resolver, ModuleRevisionId dynamicRevision);
}
