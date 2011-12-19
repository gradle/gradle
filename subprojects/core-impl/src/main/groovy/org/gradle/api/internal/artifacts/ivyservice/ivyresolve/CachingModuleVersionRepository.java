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

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactFileStore;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ForceChangeDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class CachingModuleVersionRepository implements ModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingModuleVersionRepository.class);

    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private final ArtifactResolutionCache artifactResolutionCache;
    private final ArtifactFileStore artifactFileStore;

    private final CachePolicy cachePolicy;

    private final ModuleVersionRepository delegate;

    public CachingModuleVersionRepository(ModuleVersionRepository delegate, ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache,
                                          ArtifactResolutionCache artifactResolutionCache, ArtifactFileStore artifactFileStore, CachePolicy cachePolicy) {
        this.delegate = delegate;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.moduleResolutionCache = moduleResolutionCache;
        this.artifactResolutionCache = artifactResolutionCache;
        this.artifactFileStore = artifactFileStore;
        this.cachePolicy = cachePolicy;
    }

    public String getId() {
        return delegate.getId();
    }

    public boolean isChanging(ResolvedModuleRevision revision, ResolveData resolveData) {
        return delegate.isChanging(revision, resolveData);
    }

    public boolean isLocal() {
        return delegate.isLocal();
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) {
        if (isLocal()) {
            return delegate.getDependency(dd, data);
        }

        return findModule(dd, data);
    }

    public ResolvedModuleRevision findModule(DependencyDescriptor requestedDependencyDescriptor, ResolveData resolveData) {
        DependencyDescriptor resolvedDependencyDescriptor = maybeUseCachedDynamicVersion(delegate, requestedDependencyDescriptor);
        CachedModuleLookup lookup = lookupModuleInCache(resolvedDependencyDescriptor);
        if (lookup.wasFound) {
            return lookup.module;
        }
        return resolveModule(resolvedDependencyDescriptor, requestedDependencyDescriptor, resolveData);
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

    public CachedModuleLookup lookupModuleInCache(DependencyDescriptor resolvedDependencyDescriptor) {
        ModuleRevisionId resolvedModuleVersionId = resolvedDependencyDescriptor.getDependencyRevisionId();
        ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(delegate, resolvedModuleVersionId);
        if (cachedModuleDescriptor == null) {
            return notFound();
        }
        if (cachedModuleDescriptor.isMissing()) {
            if (cachePolicy.mustRefreshMissingArtifact(cachedModuleDescriptor.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                return notFound();
            }
            LOGGER.debug("Detected non-existence of module '{}' in resolver cache", resolvedModuleVersionId);
            return found(null);
        }
        if (cachedModuleDescriptor.isChangingModule() || resolvedDependencyDescriptor.isChanging()) {
            if (cachePolicy.mustRefreshChangingModule(cachedModuleDescriptor.getModuleVersion(), cachedModuleDescriptor.getAgeMillis())) {
                // TODO:DAZ Move expiring of changing module artifacts into here, once we can rely on sha1 comparison to prevent re-download
                LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                return notFound();
            }
            LOGGER.debug("Found cached version of changing module: '{}'", resolvedModuleVersionId);
        }

        LOGGER.debug("Using cached module metadata for '{}'", resolvedModuleVersionId);
        ResolvedModuleRevision cachedModule = new ResolvedModuleRevision((DependencyResolver) delegate, (DependencyResolver) delegate, cachedModuleDescriptor.getModuleDescriptor(), null);
        return found(cachedModule);
    }

    public ResolvedModuleRevision resolveModule(DependencyDescriptor resolvedDependencyDescriptor, DependencyDescriptor requestedDependencyDescriptor1, ResolveData resolveData1) {
        ResolvedModuleRevision module = delegate.getDependency(ForceChangeDependencyDescriptor.forceChangingFlag(resolvedDependencyDescriptor, true), resolveData1);

        if (module == null) {
            moduleDescriptorCache.cacheModuleDescriptor(delegate, resolvedDependencyDescriptor.getDependencyRevisionId(), null, requestedDependencyDescriptor1.isChanging());
        } else {
            cacheArtifactFile(module);
            moduleResolutionCache.cacheModuleResolution(delegate, requestedDependencyDescriptor1.getDependencyRevisionId(), module.getId());
            moduleDescriptorCache.cacheModuleDescriptor(delegate, module.getId(), module.getDescriptor(), isChangingDependency(requestedDependencyDescriptor1, module, resolveData1));
        }
        return module;
    }

    private void cacheArtifactFile(ResolvedModuleRevision resolvedModule) {
        ArtifactOrigin artifactOrigin = resolvedModule.getReport().getArtifactOrigin();
        File artifactFile = resolvedModule.getReport().getOriginalLocalFile();
        if (artifactOrigin != null && artifactFile != null) {
            artifactFileStore.storeArtifactFile(delegate, artifactOrigin.getArtifact().getId(), artifactFile);
        }
    }

    private boolean isChangingDependency(DependencyDescriptor descriptor, ResolvedModuleRevision downloadedModule, ResolveData resolveData1) {
        if (descriptor.isChanging()) {
            return true;
        }

        return delegate.isChanging(downloadedModule, resolveData1);
    }

    private CachedModuleLookup notFound() {
        return new CachedModuleLookup(false, null);
    }

    private CachedModuleLookup found(ResolvedModuleRevision module) {
        return new CachedModuleLookup(true, module);
    }

    private static class CachedModuleLookup {
        public final boolean wasFound;
        public final ResolvedModuleRevision module;

        private CachedModuleLookup(boolean wasFound, ResolvedModuleRevision module) {
            this.module = module;
            this.wasFound = wasFound;
        }
    }

    public File download(Artifact artifact, DownloadOptions options) {
        if (isLocal()) {
            return delegate.download(artifact, options);
        }

        // Look in the cache for this resolver
        ArtifactResolutionCache.CachedArtifactResolution cachedArtifactResolution = artifactResolutionCache.getCachedArtifactResolution(delegate, artifact.getId());
        if (cachedArtifactResolution != null) {
            if (cachedArtifactResolution.getArtifactFile() == null) {
                if (!cachePolicy.mustRefreshMissingArtifact(cachedArtifactResolution.getAgeMillis())) {
                    LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact.getId());
                    return null;
                }
                // Need to check file existence since for changing modules the underlying cached artifact file will have been removed
                // Or users may manually purge the cache of files.
            } else if (cachedArtifactResolution.getArtifactFile().exists()) {
                LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact.getId(), cachedArtifactResolution.getArtifactFile());
                return cachedArtifactResolution.getArtifactFile();
            }
        }

        File artifactFile = delegate.download(artifact, options);
        LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact.getId(), artifactFile);
        return artifactResolutionCache.storeArtifactFile(delegate, artifact.getId(), artifactFile);
    }
}
