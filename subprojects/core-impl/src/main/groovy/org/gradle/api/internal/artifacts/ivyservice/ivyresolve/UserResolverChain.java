/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.ComparatorLatestStrategy;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.StringUtils;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ChangingModuleRevision;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ForceChangeDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;

public class UserResolverChain extends ChainResolver implements DependencyResolvers {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChain.class);

    private final Map<ModuleRevisionId, DependencyResolver> artifactResolvers = new HashMap<ModuleRevisionId, DependencyResolver>();
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private CachePolicy cachePolicy;

    public UserResolverChain(ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache) {
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.moduleResolutionCache = moduleResolutionCache;
    }

    public void setCachePolicy(CachePolicy cachePolicy) {
        this.cachePolicy = cachePolicy;
    }

    @Override
    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) {

        List<ModuleResolution> resolutionList = createResolutionList(dd, data);

        ModuleResolution latestCached = lookupAllInCacheAndGetLatest(resolutionList);
        if (latestCached != null) {
            LOGGER.debug("Found module '{}' in resolver cache '{}'", latestCached.getModule(), latestCached.resolver.getName());
            rememberResolverToUseForArtifactDownload(latestCached.resolver, latestCached.getModule());
            return latestCached.getModule();
        }

        // Otherwise delegate to each resolver in turn
        ModuleResolution latestResolved = resolveLatestModule(resolutionList);
        if (latestResolved != null) {
            ResolvedModuleRevision downloadedModule = latestResolved.getModule();
            LOGGER.debug("Found module '{}' using resolver '{}'", downloadedModule, downloadedModule.getArtifactResolver());
            rememberResolverToUseForArtifactDownload(downloadedModule.getArtifactResolver(), downloadedModule);
            return downloadedModule;
        }
        return null;
    }

    private List<ModuleResolution> createResolutionList(DependencyDescriptor dd, ResolveData data) {
        boolean staticVersion = !getSettings().getVersionMatcher().isDynamic(dd.getDependencyRevisionId());
        List<ModuleResolution> resolutionList = new ArrayList<ModuleResolution>();
        for (DependencyResolver resolver : getResolvers()) {
            resolutionList.add(new ModuleResolution(resolver, dd, data, staticVersion));
        }
        return resolutionList;
    }

    private ModuleResolution lookupAllInCacheAndGetLatest(List<ModuleResolution> resolutionList) {
        for (ModuleResolution moduleResolution : resolutionList) {
            moduleResolution.lookupModuleInCache();

            if (moduleResolution.getModule() != null && moduleResolution.isStaticVersion() && !moduleResolution.isGeneratedModuleDescriptor()) {
                return moduleResolution;
            }
        }

        return chooseBestResult(resolutionList);
    }

    private ModuleResolution resolveLatestModule(List<ModuleResolution> resolutionList) {

        List<RuntimeException> errors = new ArrayList<RuntimeException>();
        for (ModuleResolution moduleResolution : resolutionList) {
            try {
                moduleResolution.resolveModule();
                if (moduleResolution.getModule() != null && moduleResolution.isStaticVersion() && !moduleResolution.isGeneratedModuleDescriptor()) {
                    return moduleResolution;
                }
            } catch (RuntimeException e) {
                errors.add(e);
            }
        }

        ModuleResolution mr = chooseBestResult(resolutionList);
        if (mr == null && !errors.isEmpty()) {
            throwResolutionFailure(errors);
        }
        return mr;
    }

    private ModuleResolution chooseBestResult(List<ModuleResolution> resolutionList) {
        ModuleResolution best = null;
        for (ModuleResolution moduleResolution : resolutionList) {
            best = chooseBest(best, moduleResolution);
        }
        if (best == null || best.getModule() == null) {
            return null;
        }
        return best;
    }

    private ModuleResolution chooseBest(ModuleResolution one, ModuleResolution two) {
        if (one == null || two == null) {
            return two == null ? one : two;
        }
        if (one.getModule() == null || two.getModule() == null) {
            return two.getModule() == null ? one : two;
        }

        ComparatorLatestStrategy latestStrategy = (ComparatorLatestStrategy) getLatestStrategy();
        Comparator<ArtifactInfo> comparator = latestStrategy.getComparator();
        int comparison = comparator.compare(one, two);

        if (comparison == 0) {
            if (one.isGeneratedModuleDescriptor() && !two.isGeneratedModuleDescriptor()) {
                return two;
            }
            return one;
        }

        return comparison < 0 ? two : one;
    }

    private void rememberResolverToUseForArtifactDownload(DependencyResolver resolver, ResolvedModuleRevision cachedModule) {
        artifactResolvers.put(cachedModule.getId(), resolver);
    }

    private void throwResolutionFailure(List<RuntimeException> errors) {
        if (errors.size() == 1) {
            throw errors.get(0);
        } else {
            StringBuilder err = new StringBuilder();
            for (Exception ex : errors) {
                err.append("\t").append(StringUtils.getErrorMessage(ex)).append("\n");
            }
            err.setLength(err.length() - 1);
            throw new RuntimeException("several problems occurred while resolving :\n" + err);
        }
    }

    public List<DependencyResolver> getArtifactResolversForModule(ModuleRevisionId moduleRevisionId) {
        DependencyResolver moduleDescriptorResolver = artifactResolvers.get(moduleRevisionId);
        if (moduleDescriptorResolver != null && moduleDescriptorResolver != this) {
            return Collections.singletonList(moduleDescriptorResolver);
        }
        return getResolvers();
    }

    public boolean isLocalResolver(DependencyResolver resolver) {
        return resolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager;
    }

    @Override
    public List<DependencyResolver> getResolvers() {
        return super.getResolvers();
    }

    private class ModuleResolution implements ArtifactInfo {
        private final DependencyResolver resolver;
        private final DependencyDescriptor requestedDependencyDescriptor;
        private final ResolveData resolveData;
        private final boolean staticVersion;
        private ResolvedModuleRevision resolvedModule;
        private DependencyDescriptor resolvedDescriptor;

        public ModuleResolution(DependencyResolver resolver, DependencyDescriptor moduleDescriptor, ResolveData resolveData, boolean staticVersion) {
            this.resolver = resolver;
            this.requestedDependencyDescriptor = moduleDescriptor;
            this.resolveData = resolveData;
            this.staticVersion = staticVersion;
        }

        public boolean isStaticVersion() {
            return staticVersion;
        }

        public boolean isGeneratedModuleDescriptor() {
            if (resolvedModule == null) {
                throw new IllegalStateException();
            }
            return resolvedModule.getDescriptor().isDefault();
        }

        public void lookupModuleInCache() {
            // No caching for local resolvers
            if (isLocalResolver(resolver)) {
                resolvedDescriptor = requestedDependencyDescriptor;
                return;
            }

            resolvedDescriptor = maybeResolveDynamicRevision(resolver, requestedDependencyDescriptor);
            resolvedModule = searchForModuleInCache(resolver, resolvedDescriptor);
        }

        private DependencyDescriptor maybeResolveDynamicRevision(DependencyResolver resolver, DependencyDescriptor original) {
            ModuleRevisionId originalId = original.getDependencyRevisionId();
            ModuleResolutionCache.CachedModuleResolution cachedModuleResolution = moduleResolutionCache.getCachedModuleResolution(resolver, originalId);
            if (cachedModuleResolution != null && cachedModuleResolution.isDynamicVersion()) {
                if (cachePolicy.mustRefreshDynamicVersion(cachedModuleResolution.getResolvedModule(), cachedModuleResolution.getAgeMillis())) {
                    LOGGER.debug("Resolved revision in dynamic revision cache is expired: will perform fresh resolve of '{}'", originalId);
                    return original;
                } else {
                    LOGGER.debug("Found resolved revision in dynamic revision cache: Using '{}' for '{}'", cachedModuleResolution.getResolvedVersion(), originalId);
                    return original.clone(cachedModuleResolution.getResolvedVersion());
                }
            }
            return original;
        }

        private ResolvedModuleRevision searchForModuleInCache(DependencyResolver resolver, DependencyDescriptor dependencyDescriptor) {
            // TODO:DAZ Cache non-existence of module in resolver and use here to avoid lookup
            ModuleRevisionId moduleRevisionId = dependencyDescriptor.getDependencyRevisionId();
            ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(resolver, moduleRevisionId);
            if (cachedModuleDescriptor == null) {
                return null;
            }
            if (cachedModuleDescriptor.isChangingModule()) {
                if (cachePolicy.mustRefreshChangingModule(cachedModuleDescriptor.getModuleVersion(), cachedModuleDescriptor.getAgeMillis())) {
                    LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}'", moduleRevisionId);
                    return null;
                }
                LOGGER.debug("Found cached version of changing module: '{}'", moduleRevisionId);
            }

            LOGGER.debug("Using cached module metadata for '{}'", moduleRevisionId);
            return new ResolvedModuleRevision(resolver, resolver, cachedModuleDescriptor.getModuleDescriptor(), null);
        }

        public void resolveModule() {
            try {
                resolvedModule = resolver.getDependency(ForceChangeDependencyDescriptor.forceChangingFlag(resolvedDescriptor, true), resolveData);

                if (resolvedModule == null || isLocalResolver(resolver)) {
                    return;
                }

                moduleResolutionCache.cacheModuleResolution(resolver, requestedDependencyDescriptor.getDependencyRevisionId(), resolvedModule.getId());
                // TODO:DAZ Handle null: record missing module
                moduleDescriptorCache.cacheModuleDescriptor(resolver, resolvedModule.getDescriptor(), isChangingModule(requestedDependencyDescriptor, resolvedModule));

            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        public ResolvedModuleRevision getModule() {
            return resolvedModule;
        }

        public long getLastModified() {
            return resolvedModule.getPublicationDate().getTime();
        }

        public String getRevision() {
            return resolvedModule.getId().getRevision();
        }

        private boolean isChangingModule(DependencyDescriptor descriptor, ResolvedModuleRevision downloadedModule) {
            return descriptor.isChanging() || downloadedModule instanceof ChangingModuleRevision;
        }
    }
}
