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
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ForceChangeDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.api.internal.externalresource.cached.CachedArtifact;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryKey;
import org.gradle.internal.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;

public class CachingModuleVersionRepository implements LocalAwareModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingModuleVersionRepository.class);

    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private final CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex;

    private final CachePolicy cachePolicy;

    private final ModuleVersionRepository delegate;
    private final TimeProvider timeProvider;

    public CachingModuleVersionRepository(ModuleVersionRepository delegate, ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache,
                                          CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex,
                                          CachePolicy cachePolicy, TimeProvider timeProvider) {
        this.delegate = delegate;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.moduleResolutionCache = moduleResolutionCache;
        this.artifactAtRepositoryCachedResolutionIndex = artifactAtRepositoryCachedResolutionIndex;
        this.timeProvider = timeProvider;
        this.cachePolicy = cachePolicy;
    }

    public String getId() {
        return delegate.getId();
    }

    public String getName() {
        return delegate.getName();
    }

    @Override
    public String toString() {
        return "Caching " + delegate.toString();
    }

    public boolean isLocal() {
        return delegate.isLocal();
    }

    public void getLocalDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionDescriptor result) {
        DependencyDescriptor resolvedDependencyDescriptor = maybeUseCachedDynamicVersion(delegate, dependencyDescriptor);
        lookupModuleInCache(delegate, resolvedDependencyDescriptor, result);
    }

    public void getDependency(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionDescriptor result) {
        delegate.getDependency(ForceChangeDependencyDescriptor.forceChangingFlag(dependencyDescriptor, true), result);
        switch (result.getState()) {
            case Missing:
                moduleDescriptorCache.cacheModuleDescriptor(delegate, dependencyDescriptor.getDependencyRevisionId(), null, dependencyDescriptor.isChanging());
                break;
            case Resolved:
                moduleResolutionCache.cacheModuleResolution(delegate, dependencyDescriptor.getDependencyRevisionId(), result.getId());
                moduleDescriptorCache.cacheModuleDescriptor(delegate, result.getId(), result.getDescriptor(), isChangingDependency(dependencyDescriptor, result));
                break;
            case Failed:
                break;
            default:
                throw new IllegalStateException("Unexpected resolve state: " + result.getState());
        }
    }

    private DependencyDescriptor maybeUseCachedDynamicVersion(ModuleVersionRepository repository, DependencyDescriptor original) {
        ModuleRevisionId originalId = original.getDependencyRevisionId();
        ModuleResolutionCache.CachedModuleResolution cachedModuleResolution = moduleResolutionCache.getCachedModuleResolution(repository, originalId);
        if (cachedModuleResolution != null && cachedModuleResolution.isDynamicVersion()) {
            ModuleVersionSelector selector = createModuleVersionSelector(originalId);
            ModuleVersionIdentifier resolvedVersion = cachedModuleResolution.getResolvedModule() == null ? null : cachedModuleResolution.getResolvedModule().getId();
            if (cachePolicy.mustRefreshDynamicVersion(selector, resolvedVersion, cachedModuleResolution.getAgeMillis())) {
                LOGGER.debug("Resolved revision in dynamic revision cache is expired: will perform fresh resolve of '{}' in '{}'", selector, repository.getName());
                return original;
            } else {
                LOGGER.debug("Found resolved revision in dynamic revision cache of '{}': Using '{}' for '{}'",
                        new Object[]{repository.getName(), cachedModuleResolution.getResolvedVersion(), originalId});
                return original.clone(cachedModuleResolution.getResolvedVersion());
            }
        }
        return original;
    }

    public void lookupModuleInCache(ModuleVersionRepository repository, DependencyDescriptor resolvedDependencyDescriptor, BuildableModuleVersionDescriptor result) {
        ModuleRevisionId resolvedModuleVersionId = resolvedDependencyDescriptor.getDependencyRevisionId();
        ModuleVersionIdentifier moduleVersionIdentifier = createModuleVersionIdentifier(resolvedModuleVersionId);
        ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(repository, resolvedModuleVersionId);
        if (cachedModuleDescriptor == null) {
            return;
        }
        if (cachedModuleDescriptor.isMissing()) {
            if (cachePolicy.mustRefreshModule(moduleVersionIdentifier, null, resolvedModuleVersionId, cachedModuleDescriptor.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}' in '{}'", resolvedModuleVersionId, repository.getName());
                return;
            }
            LOGGER.debug("Detected non-existence of module '{}' in resolver cache '{}'", resolvedModuleVersionId, repository.getName());
            if (cachedModuleDescriptor.getAgeMillis() == 0) {
                // Verified since the start of this build, assume still missing
                result.missing();
            } else {
                // Was missing last time we checked
                result.probablyMissing();
            }
            return;
        }
        if (cachedModuleDescriptor.isChangingModule() || resolvedDependencyDescriptor.isChanging()) {
            if (cachePolicy.mustRefreshChangingModule(moduleVersionIdentifier, cachedModuleDescriptor.getModuleVersion(), cachedModuleDescriptor.getAgeMillis())) {
                expireArtifactsForChangingModule(repository, cachedModuleDescriptor.getModuleDescriptor());
                LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}' in '{}'", resolvedModuleVersionId, repository.getName());
                return;
            }
            LOGGER.debug("Found cached version of changing module '{}' in '{}'", resolvedModuleVersionId, repository.getName());
        } else {
            if (cachePolicy.mustRefreshModule(moduleVersionIdentifier, cachedModuleDescriptor.getModuleVersion(), null, cachedModuleDescriptor.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for module must be refreshed: will perform fresh resolve of '{}' in '{}'", resolvedModuleVersionId, repository.getName());
                return;
            }
        }

        LOGGER.debug("Using cached module metadata for module '{}' in '{}'", resolvedModuleVersionId, repository.getName());
        // TODO:DAZ Could provide artifact metadata and file here from artifactFileStore (it's not needed currently)
        result.resolved(cachedModuleDescriptor.getModuleDescriptor(), cachedModuleDescriptor.isChangingModule());
    }

    private void expireArtifactsForChangingModule(ModuleVersionRepository repository, ModuleDescriptor descriptor) {
        for (Artifact artifact : descriptor.getAllArtifacts()) {
            artifactAtRepositoryCachedResolutionIndex.clear(new ArtifactAtRepositoryKey(repository, artifact.getId()));
        }
    }

    private boolean isChangingDependency(DependencyDescriptor descriptor, ModuleVersionDescriptor downloadedModule) {
        if (descriptor.isChanging()) {
            return true;
        }

        return downloadedModule.isChanging();
    }

    public void resolve(Artifact artifact, BuildableArtifactResolveResult result) {
        ArtifactAtRepositoryKey resolutionCacheIndexKey = new ArtifactAtRepositoryKey(delegate, artifact.getId());
        // Look in the cache for this resolver
        CachedArtifact cached = artifactAtRepositoryCachedResolutionIndex.lookup(resolutionCacheIndexKey);

        final ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(delegate, artifact.getModuleRevisionId());
        final BigInteger descriptorHash = cachedModuleDescriptor == null ? BigInteger.valueOf(-1) : cachedModuleDescriptor.getDescriptorHash();
        if (cached != null) {
            ArtifactIdentifier artifactIdentifier = createArtifactIdentifier(artifact);
            long age = timeProvider.getCurrentTime() - cached.getCachedAt();
            if (cached.isMissing()) {
                if (!cachePolicy.mustRefreshArtifact(artifactIdentifier, null, age, descriptorHash.equals(cached.getDescriptorHash()))) {
                    LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact.getId());
                    result.notFound(artifact);
                    return;
                }
            } else {
                File cachedArtifactFile = cached.getCachedFile();
                if (!cachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, age, descriptorHash.equals(cached.getDescriptorHash()))) {
                    LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact.getId(), cachedArtifactFile);
                    result.resolved(cachedArtifactFile);
                    return;
                }
            }
        }

        delegate.resolve(artifact, result);
        LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact.getId(), delegate);

        if (result.getFailure() instanceof ArtifactNotFoundException) {
            artifactAtRepositoryCachedResolutionIndex.storeMissing(resolutionCacheIndexKey, descriptorHash);
        } else {
            artifactAtRepositoryCachedResolutionIndex.store(resolutionCacheIndexKey, result.getFile(), descriptorHash);
        }
    }

    private ModuleVersionSelector createModuleVersionSelector(ModuleRevisionId moduleRevisionId) {
        return new DefaultModuleVersionSelector(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    private ModuleVersionIdentifier createModuleVersionIdentifier(ModuleRevisionId moduleRevisionId) {
        return new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    private ArtifactIdentifier createArtifactIdentifier(Artifact artifact) {
        ModuleVersionIdentifier moduleVersionIdentifier = createModuleVersionIdentifier(artifact.getModuleRevisionId());
        return new DefaultArtifactIdentifier(moduleVersionIdentifier, artifact.getName(), artifact.getType(), artifact.getExt(), artifact.getExtraAttribute("classifier"));
    }
}
