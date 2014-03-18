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

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;
import org.gradle.api.internal.externalresource.cached.CachedArtifact;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.api.internal.externalresource.ivy.ArtifactAtRepositoryKey;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.util.Set;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

public class CachingModuleVersionRepository implements LocalAwareModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingModuleVersionRepository.class);

    private final ModuleVersionsCache moduleVersionsCache;
    private final ModuleMetaDataCache moduleMetaDataCache;
    private final CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex;

    private final CachePolicy cachePolicy;

    private final ModuleVersionRepository delegate;
    private final BuildCommencedTimeProvider timeProvider;
    private final ModuleMetadataProcessor metadataProcessor;
    private final Transformer<ModuleIdentifier, ModuleVersionSelector> moduleExtractor;

    public CachingModuleVersionRepository(ModuleVersionRepository delegate, ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache,
                                          CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex,
                                          CachePolicy cachePolicy, BuildCommencedTimeProvider timeProvider,
                                          ModuleMetadataProcessor metadataProcessor, Transformer<ModuleIdentifier, ModuleVersionSelector> moduleExtractor) {
        this.delegate = delegate;
        this.moduleMetaDataCache = moduleMetaDataCache;
        this.moduleVersionsCache = moduleVersionsCache;
        this.artifactAtRepositoryCachedResolutionIndex = artifactAtRepositoryCachedResolutionIndex;
        this.timeProvider = timeProvider;
        this.cachePolicy = cachePolicy;
        this.metadataProcessor = metadataProcessor;
        this.moduleExtractor = moduleExtractor;
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

    public void localListModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        final ModuleIdentifier moduleId = moduleExtractor.transform(requested);
        ModuleVersionsCache.CachedModuleVersionList cachedModuleVersionList = moduleVersionsCache.getCachedModuleResolution(delegate, moduleId);
        if (cachedModuleVersionList != null) {
            ModuleVersionListing versionList = cachedModuleVersionList.getModuleVersions();
            Set<ModuleVersionIdentifier> versions = CollectionUtils.collect(versionList.getVersions(), new Transformer<ModuleVersionIdentifier, Versioned>() {
                public ModuleVersionIdentifier transform(Versioned original) {
                    return new DefaultModuleVersionIdentifier(moduleId, original.getVersion());
                }
            });
            if (cachePolicy.mustRefreshVersionList(moduleId, versions, cachedModuleVersionList.getAgeMillis())) {
                LOGGER.debug("Version listing in dynamic revision cache is expired: will perform fresh resolve of '{}' in '{}'", requested, delegate.getName());
            } else {
                if (cachedModuleVersionList.getAgeMillis() == 0) {
                    // Verified since the start of this build, assume still missing
                    result.listed(versionList);
                } else {
                    // Was missing last time we checked
                    result.probablyListed(versionList);
                }
            }
        }
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        delegate.listModuleVersions(dependency, result);
        switch (result.getState()) {
            case Listed:
                ModuleIdentifier moduleId = moduleExtractor.transform(dependency.getRequested());
                ModuleVersionListing versionList = result.getVersions();
                moduleVersionsCache.cacheModuleVersionList(delegate, moduleId, versionList);
                break;
            case Failed:
                break;
            default:
                throw new IllegalStateException("Unexpected state on listModuleVersions: " + result.getState());
        }
    }

    public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        lookupModuleInCache(delegate, dependency, result);
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        DependencyMetaData forced = dependency.withChanging();
        delegate.getDependency(forced, result);
        switch (result.getState()) {
            case Missing:
                ModuleRevisionId dependencyRevisionId = dependency.getDescriptor().getDependencyRevisionId();
                ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(dependencyRevisionId);
                moduleMetaDataCache.cacheMissing(delegate, moduleVersionIdentifier);
                break;
            case Resolved:
                MutableModuleVersionMetaData metaData = result.getMetaData();
                ModuleSource moduleSource = result.getModuleSource();
                ModuleMetaDataCache.CachedMetaData cachedMetaData = moduleMetaDataCache.cacheMetaData(delegate, metaData, moduleSource);
                metadataProcessor.process(metaData);
                result.setModuleSource(new CachingModuleSource(cachedMetaData.getDescriptorHash(), dependency.isChanging() || metaData.isChanging(), moduleSource));
                break;
            case Failed:
                break;
            default:
                throw new IllegalStateException("Unexpected resolve state: " + result.getState());
        }
    }

    private void lookupModuleInCache(ModuleVersionRepository repository, DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        ModuleRevisionId resolvedModuleVersionId = dependency.getDescriptor().getDependencyRevisionId();
        ModuleVersionIdentifier moduleVersionIdentifier = newId(resolvedModuleVersionId);
        ModuleMetaDataCache.CachedMetaData cachedMetaData = moduleMetaDataCache.getCachedModuleDescriptor(repository, moduleVersionIdentifier);
        if (cachedMetaData == null) {
            return;
        }
        if (cachedMetaData.isMissing()) {
            if (cachePolicy.mustRefreshModule(moduleVersionIdentifier, null, resolvedModuleVersionId, cachedMetaData.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}' in '{}'", resolvedModuleVersionId, repository.getName());
                return;
            }
            LOGGER.debug("Detected non-existence of module '{}' in resolver cache '{}'", resolvedModuleVersionId, repository.getName());
            if (cachedMetaData.getAgeMillis() == 0) {
                // Verified since the start of this build, assume still missing
                result.missing();
            } else {
                // Was missing last time we checked
                result.probablyMissing();
            }
            return;
        }
        MutableModuleVersionMetaData metaData = cachedMetaData.getMetaData();
        metadataProcessor.process(metaData);
        if (dependency.isChanging() || metaData.isChanging()) {
            if (cachePolicy.mustRefreshChangingModule(moduleVersionIdentifier, cachedMetaData.getModuleVersion(), cachedMetaData.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}' in '{}'", resolvedModuleVersionId, repository.getName());
                return;
            }
            LOGGER.debug("Found cached version of changing module '{}' in '{}'", resolvedModuleVersionId, repository.getName());
        } else {
            if (cachePolicy.mustRefreshModule(moduleVersionIdentifier, cachedMetaData.getModuleVersion(), null, cachedMetaData.getAgeMillis())) {
                LOGGER.debug("Cached meta-data for module must be refreshed: will perform fresh resolve of '{}' in '{}'", resolvedModuleVersionId, repository.getName());
                return;
            }
        }

        LOGGER.debug("Using cached module metadata for module '{}' in '{}'", resolvedModuleVersionId, repository.getName());
        result.resolved(metaData, new CachingModuleSource(cachedMetaData.getDescriptorHash(), metaData.isChanging(), cachedMetaData.getModuleSource()));
    }

    public void resolveModuleArtifacts(ModuleVersionMetaData moduleMetaData, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        // TODO:DAZ Add caching
        delegate.resolveModuleArtifacts(moduleMetaData, context, result);
    }

    public void resolveArtifact(ModuleVersionMetaData moduleMetaData, ModuleVersionArtifactMetaData artifact, BuildableArtifactResolveResult result) {
        ArtifactAtRepositoryKey resolutionCacheIndexKey = new ArtifactAtRepositoryKey(delegate.getId(), artifact.getId());
        // Look in the cache for this resolver
        CachedArtifact cached = artifactAtRepositoryCachedResolutionIndex.lookup(resolutionCacheIndexKey);
        final CachingModuleSource cachedModuleSource = (CachingModuleSource) moduleMetaData.getSource();
        final BigInteger descriptorHash = cachedModuleSource.getDescriptorHash();
        if (cached != null) {
            long age = timeProvider.getCurrentTime() - cached.getCachedAt();
            final boolean isChangingModule = cachedModuleSource.isChangingModule();
            ArtifactIdentifier artifactIdentifier = artifact.toArtifactIdentifier();
            if (cached.isMissing()) {
                if (!cachePolicy.mustRefreshArtifact(artifactIdentifier, null, age, isChangingModule, descriptorHash.equals(cached.getDescriptorHash()))) {
                    LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact);
                    result.notFound(artifact.getId());
                    return;
                }
            } else {
                File cachedArtifactFile = cached.getCachedFile();
                if (!cachePolicy.mustRefreshArtifact(artifactIdentifier, cachedArtifactFile, age, isChangingModule, descriptorHash.equals(cached.getDescriptorHash()))) {
                    LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact, cachedArtifactFile);
                    result.resolved(cachedArtifactFile);
                    return;
                }
            }
        }

        delegate.resolveArtifact(moduleMetaData.withSource(cachedModuleSource.getDelegate()), artifact, result);
        LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact, delegate.getName());

        if (result.getFailure() == null) {
            artifactAtRepositoryCachedResolutionIndex.store(resolutionCacheIndexKey, result.getFile(), descriptorHash);
        } else if (result.getFailure() instanceof ArtifactNotFoundException) {
            artifactAtRepositoryCachedResolutionIndex.storeMissing(resolutionCacheIndexKey, descriptorHash);
        }
    }

    static class CachingModuleSource implements ModuleSource {
        private final BigInteger descriptorHash;
        private final boolean changingModule;
        private final ModuleSource delegate;

        public CachingModuleSource(BigInteger descriptorHash, boolean changingModule, ModuleSource delegate) {
            this.delegate = delegate;
            this.descriptorHash = descriptorHash;
            this.changingModule = changingModule;
        }

        public BigInteger getDescriptorHash() {
            return descriptorHash;
        }

        public boolean isChangingModule() {
            return changingModule;
        }

        public ModuleSource getDelegate() {
            return delegate;
        }
    }
}
