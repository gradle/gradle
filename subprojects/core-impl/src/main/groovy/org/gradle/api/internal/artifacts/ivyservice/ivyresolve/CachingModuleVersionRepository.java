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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
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

    private final CachePolicy cachePolicy;

    private final ModuleVersionRepository delegate;

    public CachingModuleVersionRepository(ModuleVersionRepository delegate, ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache,
                                          ArtifactResolutionCache artifactResolutionCache, CachePolicy cachePolicy) {
        this.delegate = delegate;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.moduleResolutionCache = moduleResolutionCache;
        this.artifactResolutionCache = artifactResolutionCache;
        this.cachePolicy = cachePolicy;
    }

    public String getId() {
        return delegate.getId();
    }

    @Override
    public String toString() {
        return "Caching " + delegate.toString();
    }

    public boolean isLocal() {
        return delegate.isLocal();
    }

    public ModuleVersionDescriptor getDependency(DependencyDescriptor dd) {
        if (isLocal()) {
            return delegate.getDependency(dd);
        }

        return findModule(dd);
    }

    public ModuleVersionDescriptor findModule(DependencyDescriptor requestedDependencyDescriptor) {
        DependencyDescriptor resolvedDependencyDescriptor = maybeUseCachedDynamicVersion(delegate, requestedDependencyDescriptor);
        CachedModuleLookup lookup = lookupModuleInCache(resolvedDependencyDescriptor);
        if (lookup.wasFound) {
            return lookup.module;
        }
        return resolveModule(resolvedDependencyDescriptor, requestedDependencyDescriptor);
    }

    private DependencyDescriptor maybeUseCachedDynamicVersion(ModuleVersionRepository repository, DependencyDescriptor original) {
        ModuleRevisionId originalId = original.getDependencyRevisionId();
        ModuleResolutionCache.CachedModuleResolution cachedModuleResolution = moduleResolutionCache.getCachedModuleResolution(repository, originalId);
        if (cachedModuleResolution != null && cachedModuleResolution.isDynamicVersion()) {
            ModuleVersionSelector selector = createModuleVersionSelector(originalId);
            ModuleVersionIdentifier resolvedVersion = cachedModuleResolution.getResolvedModule() == null ? null : cachedModuleResolution.getResolvedModule().getId();
            if (cachePolicy.mustRefreshDynamicVersion(selector, resolvedVersion, cachedModuleResolution.getAgeMillis())) {
                LOGGER.debug("Resolved revision in dynamic revision cache is expired: will perform fresh resolve of '{}'", selector);
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
        ModuleVersionIdentifier moduleVersionIdentifier = createModuleVersionIdentifier(resolvedModuleVersionId);
        ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(delegate, resolvedModuleVersionId);
        if (cachedModuleDescriptor == null) {
            return notFound();
        }
        if (cachedModuleDescriptor.isMissing()) {
            if (cachePolicy.mustRefreshModule(moduleVersionIdentifier, null, cachedModuleDescriptor.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                return notFound();
            }
            LOGGER.debug("Detected non-existence of module '{}' in resolver cache", resolvedModuleVersionId);
            return found(null);
        }
        if (cachedModuleDescriptor.isChangingModule() || resolvedDependencyDescriptor.isChanging()) {
            if (cachePolicy.mustRefreshChangingModule(moduleVersionIdentifier, cachedModuleDescriptor.getModuleVersion(), cachedModuleDescriptor.getAgeMillis())) {
                expireArtifactsForChangingModule(delegate, cachedModuleDescriptor.getModuleDescriptor());
                LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                return notFound();
            }
            LOGGER.debug("Found cached version of changing module: '{}'", resolvedModuleVersionId);
        } else {
            if (cachePolicy.mustRefreshModule(moduleVersionIdentifier, cachedModuleDescriptor.getModuleVersion(), cachedModuleDescriptor.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for module must be refreshed: will perform fresh resolve of '{}'", resolvedModuleVersionId);
                return notFound();
            }
        }

        LOGGER.debug("Using cached module metadata for '{}'", resolvedModuleVersionId);
        // TODO:DAZ Could provide artifact metadata and file here from artifactFileStore (it's not needed currently)
        ModuleVersionDescriptor cachedModule = new DefaultModuleVersionDescriptor(cachedModuleDescriptor.getModuleDescriptor(), cachedModuleDescriptor.isChangingModule());
        return found(cachedModule);
    }

    private void expireArtifactsForChangingModule(ModuleVersionRepository repository, ModuleDescriptor descriptor) {
        for (Artifact artifact : descriptor.getAllArtifacts()) {
            artifactResolutionCache.expireCachedArtifactResolution(repository, artifact.getId());
        }
    }
    
    public ModuleVersionDescriptor resolveModule(DependencyDescriptor resolvedDependencyDescriptor, DependencyDescriptor requestedDependencyDescriptor) {
        ModuleVersionDescriptor module = delegate.getDependency(ForceChangeDependencyDescriptor.forceChangingFlag(resolvedDependencyDescriptor, true));

        if (module == null) {
            moduleDescriptorCache.cacheModuleDescriptor(delegate, resolvedDependencyDescriptor.getDependencyRevisionId(), null, requestedDependencyDescriptor.isChanging());
        } else {
            moduleResolutionCache.cacheModuleResolution(delegate, requestedDependencyDescriptor.getDependencyRevisionId(), module.getId());
            moduleDescriptorCache.cacheModuleDescriptor(delegate, module.getId(), module.getDescriptor(), isChangingDependency(requestedDependencyDescriptor, module));
        }
        return module;
    }

    private boolean isChangingDependency(DependencyDescriptor descriptor, ModuleVersionDescriptor downloadedModule) {
        if (descriptor.isChanging()) {
            return true;
        }

        return downloadedModule.isChanging();
    }

    private CachedModuleLookup notFound() {
        return new CachedModuleLookup(false, null);
    }

    private CachedModuleLookup found(ModuleVersionDescriptor module) {
        return new CachedModuleLookup(true, module);
    }

    private static class CachedModuleLookup {
        public final boolean wasFound;
        public final ModuleVersionDescriptor module;

        private CachedModuleLookup(boolean wasFound, ModuleVersionDescriptor module) {
            this.module = module;
            this.wasFound = wasFound;
        }
    }

    public File download(Artifact artifact) {
        if (isLocal()) {
            return delegate.download(artifact);
        }

        // Look in the cache for this resolver
        ArtifactResolutionCache.CachedArtifactResolution cachedArtifactResolution = artifactResolutionCache.getCachedArtifactResolution(delegate, artifact.getId());
        if (cachedArtifactResolution != null) {
            ArtifactIdentifier artifactIdentifier = createArtifactIdentifier(artifact);
            File cachedArtifactFile = cachedArtifactResolution.getArtifactFile();
            if (cachedArtifactFile == null) {
                if (!cachePolicy.mustRefreshArtifact(artifactIdentifier, null, cachedArtifactResolution.getAgeMillis())) {
                    LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact.getId());
                    return null;
                }
            } else if (cachedArtifactFile.exists()) {
                if (!cachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, cachedArtifactResolution.getAgeMillis())) {
                    LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact.getId(), cachedArtifactFile);
                    return cachedArtifactFile;
                }
            }
        }

        File artifactFile = delegate.download(artifact);
        LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact.getId(), artifactFile);
        return artifactResolutionCache.storeArtifactFile(delegate, artifact.getId(), artifactFile);
    }

    private ModuleVersionSelector createModuleVersionSelector(ModuleRevisionId moduleRevisionId) {
        return new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    private ModuleVersionIdentifier createModuleVersionIdentifier(ModuleRevisionId moduleRevisionId) {
        return new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    private ArtifactIdentifier createArtifactIdentifier(Artifact artifact) {
        ModuleVersionIdentifier moduleVersionIdentifier = createModuleVersionIdentifier(artifact.getModuleRevisionId());
        return new DefaultArtifactIdentifier(moduleVersionIdentifier, artifact.getName(), artifact.getType(), artifact.getExt(), artifact.getExtraAttribute("classifier"));
    }
}
