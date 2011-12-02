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
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ForceChangeDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UserResolverChain extends ChainResolver implements DependencyResolvers {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserResolverChain.class);

    private final Map<ModuleRevisionId, ModuleVersionRepository> artifactRepositories = new HashMap<ModuleRevisionId, ModuleVersionRepository>();
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
    public void add(DependencyResolver resolver) {
        if (!(resolver instanceof ModuleVersionRepository)) {
            throw new IllegalArgumentException("Can only add ModuleVersionRepository instances.");
        }
        super.add(resolver);
    }

    @Override
    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) {

        List<ModuleResolution> resolutionList = createResolutionList(dd, data);

        // Otherwise delegate to each resolver in turn
        ModuleResolution latestResolved = findLatestModule(resolutionList);
        if (latestResolved != null) {
            ResolvedModuleRevision downloadedModule = latestResolved.getModule();
            LOGGER.debug("Found module '{}' using resolver '{}'", downloadedModule, downloadedModule.getArtifactResolver());
            rememberResolverToUseForArtifactDownload(latestResolved.repository, downloadedModule);
            return downloadedModule;
        }
        return null;
    }

    private List<ModuleResolution> createResolutionList(DependencyDescriptor dd, ResolveData data) {
        boolean staticVersion = !getSettings().getVersionMatcher().isDynamic(dd.getDependencyRevisionId());
        List<ModuleResolution> resolutionList = new ArrayList<ModuleResolution>();
        for (ModuleVersionRepository resolver : getResolvers()) {
            resolutionList.add(new ModuleResolution(resolver, dd, data, staticVersion));
        }
        return resolutionList;
    }

    private ModuleResolution findLatestModule(List<ModuleResolution> resolutionList) {

        List<RuntimeException> errors = new ArrayList<RuntimeException>();
        for (ModuleResolution moduleResolution : resolutionList) {
            try {
                moduleResolution.findModule();
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

    private void rememberResolverToUseForArtifactDownload(ModuleVersionRepository repository, ResolvedModuleRevision cachedModule) {
        artifactRepositories.put(cachedModule.getId(), repository);
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

    public List<ModuleVersionRepository> getArtifactResolversForModule(ModuleRevisionId moduleRevisionId) {
        ModuleVersionRepository moduleDescriptorRepository = artifactRepositories.get(moduleRevisionId);
        if (moduleDescriptorRepository != null && moduleDescriptorRepository != this) {
            return Collections.singletonList(moduleDescriptorRepository);
        }
        return getResolvers();
    }

    @Override
    public List<ModuleVersionRepository> getResolvers() {
        return super.getResolvers();
    }

    private class ModuleResolution implements ArtifactInfo {
        private final ModuleVersionRepository repository;
        private final DependencyDescriptor requestedDependencyDescriptor;
        private final ResolveData resolveData;
        private final boolean staticVersion;
        private DependencyDescriptor resolvedDependencyDescriptor;
        private ResolvedModuleRevision resolvedModule;

        public ModuleResolution(ModuleVersionRepository repository, DependencyDescriptor moduleDescriptor, ResolveData resolveData, boolean staticVersion) {
            this.repository = repository;
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

        public void findModule() {
            resolvedDependencyDescriptor = null;
            resolvedModule = null;
            
            resolveModuleSelector();
            if (!lookupModuleInCache()) {
                resolveModule();
            }
        }

        private void resolveModuleSelector() {
            if (bypassCache()) {
                // No caching for local resolvers
                resolvedDependencyDescriptor = requestedDependencyDescriptor;
                return;
            }

            resolvedDependencyDescriptor = maybeUseCachedDynamicVersion(repository, requestedDependencyDescriptor);
        }

        private DependencyDescriptor maybeUseCachedDynamicVersion(ModuleVersionRepository repository, DependencyDescriptor original) {
            ModuleRevisionId originalId = original.getDependencyRevisionId();
            ModuleResolutionCache.CachedModuleResolution cachedModuleResolution = moduleResolutionCache.getCachedModuleResolution(repository, originalId);
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

        public boolean lookupModuleInCache() {
            // No caching for local resolvers
            if (bypassCache()) {
                return false;
            }

            ModuleRevisionId resolvedModuleVersionId = resolvedDependencyDescriptor.getDependencyRevisionId();
            ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(repository, resolvedModuleVersionId);
            if (cachedModuleDescriptor == null) {
                return false;
            }
            if (cachedModuleDescriptor.isMissing()) {
                if (cachePolicy.mustRefreshMissingArtifact(cachedModuleDescriptor.getAgeMillis())) {
                    LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                    return false;
                }
                LOGGER.debug("Detected non-existence of module '{}' in resolver cache", resolvedModuleVersionId);
                return true;
            }
            if (cachedModuleDescriptor.isChangingModule()) {
                if (cachePolicy.mustRefreshChangingModule(cachedModuleDescriptor.getModuleVersion(), cachedModuleDescriptor.getAgeMillis())) {
                    LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                    return false;
                }
                LOGGER.debug("Found cached version of changing module: '{}'", resolvedModuleVersionId);
            }

            LOGGER.debug("Using cached module metadata for '{}'", resolvedModuleVersionId);
            resolvedModule = new ResolvedModuleRevision((DependencyResolver) repository, (DependencyResolver) repository, cachedModuleDescriptor.getModuleDescriptor(), null);
            return true;
        }

        public void resolveModule() {
            resolvedModule = repository.getDependency(ForceChangeDependencyDescriptor.forceChangingFlag(resolvedDependencyDescriptor, true), resolveData);

            // No caching for local resolvers
            if (bypassCache()) {
                return;
            }

            if (resolvedModule == null) {
                moduleDescriptorCache.cacheModuleDescriptor(repository, resolvedDependencyDescriptor.getDependencyRevisionId(), null, requestedDependencyDescriptor.isChanging());
            } else {
                moduleResolutionCache.cacheModuleResolution(repository, requestedDependencyDescriptor.getDependencyRevisionId(), resolvedModule.getId());
                moduleDescriptorCache.cacheModuleDescriptor(repository, resolvedModule.getId(), resolvedModule.getDescriptor(), isChangingDependency(requestedDependencyDescriptor, resolvedModule));
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

        private boolean isChangingDependency(DependencyDescriptor descriptor, ResolvedModuleRevision downloadedModule) {
            if (descriptor.isChanging()) {
                return true;
            }

            return repository.isChanging(downloadedModule, resolveData);
        }

        private boolean bypassCache() {
            return repository.isLocal();
        }
    }
}
