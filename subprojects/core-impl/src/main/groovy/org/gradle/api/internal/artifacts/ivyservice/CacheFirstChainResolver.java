package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.text.ParseException;
import java.util.List;

public class CacheFirstChainResolver extends ChainResolver {

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data)
            throws ParseException {

        // First attempt to locate the module in a resolver cache
        for (DependencyResolver resolver : getResolvers()) {
            ResolvedModuleRevision cachedModule = findModuleInCache(resolver, dd, data);
            if (cachedModule != null) {
                return cachedModule;
            }
        }

        // Otherwise delegate to the regular chain (each resolver will re-check cache)
        return super.getDependency(dd, data);
    }

    private ResolvedModuleRevision findModuleInCache(DependencyResolver resolver, DependencyDescriptor dd, ResolveData resolveData) {
        // TODO construct cache options relevant for resolver and resolveData (use details from AbstractResolver if appropriate)
        return resolver.getRepositoryCacheManager().findModuleInCache(dd, dd.getDependencyRevisionId(), new CacheMetadataOptions(), resolver.getName());
    }

    @Override
    public List<DependencyResolver> getResolvers() {
        return super.getResolvers();
    }
}
