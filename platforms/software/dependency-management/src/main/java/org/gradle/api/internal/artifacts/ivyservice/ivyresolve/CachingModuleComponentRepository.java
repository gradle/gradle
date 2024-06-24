/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleRepositoryCaches;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ArtifactAtRepositoryKey;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.CachedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.CachedArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.MutableModuleSources;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resolve.ArtifactNotFoundException;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult;
import org.gradle.util.internal.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A ModuleComponentRepository that loads and saves resolution results in the dependency resolution cache.
 *
 * The `LocateInCacheRepositoryAccess` provided by {@link #getLocalAccess()} will attempt to handle any resolution request
 * directly from the cache, checking for cache expiry based on the `ResolutionStrategy` in operation.
 *
 * The `ResolveAndCacheRepositoryAccess` provided by {@link #getRemoteAccess()} will first delegate any resolution request,
 * and then store the result in the dependency resolution cache.
 */
public class CachingModuleComponentRepository implements ModuleComponentRepository<ModuleComponentGraphResolveState> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingModuleComponentRepository.class);

    private final ModuleVersionsCache moduleVersionsCache;
    private final ModuleMetadataCache moduleMetadataCache;
    private final ModuleArtifactsCache moduleArtifactsCache;
    private final ModuleArtifactCache moduleArtifactCache;

    private final ModuleComponentRepository<ModuleComponentResolveMetadata> delegate;
    private final ModuleComponentGraphResolveStateFactory resolveStateFactory;
    private final CachePolicy cachePolicy;
    private final BuildCommencedTimeProvider timeProvider;
    private final ComponentMetadataProcessor metadataProcessor;
    private final ChangingValueDependencyResolutionListener listener;
    private final LocateInCacheRepositoryAccess locateInCacheRepositoryAccess = new LocateInCacheRepositoryAccess();
    private final ResolveAndCacheRepositoryAccess resolveAndCacheRepositoryAccess = new ResolveAndCacheRepositoryAccess();

    public CachingModuleComponentRepository(
        ModuleComponentRepository<ModuleComponentResolveMetadata> delegate,
        ModuleRepositoryCaches caches,
        ModuleComponentGraphResolveStateFactory resolveStateFactory,
        CachePolicy cachePolicy,
        BuildCommencedTimeProvider timeProvider,
        ComponentMetadataProcessor metadataProcessor,
        ChangingValueDependencyResolutionListener listener
    ) {
        this.delegate = delegate;
        this.moduleMetadataCache = caches.moduleMetadataCache;
        this.moduleVersionsCache = caches.moduleVersionsCache;
        this.moduleArtifactsCache = caches.moduleArtifactsCache;
        this.moduleArtifactCache = caches.moduleArtifactCache;
        this.resolveStateFactory = resolveStateFactory;
        this.cachePolicy = cachePolicy;
        this.timeProvider = timeProvider;
        this.metadataProcessor = metadataProcessor;
        this.listener = listener;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> getLocalAccess() {
        return locateInCacheRepositoryAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> getRemoteAccess() {
        return resolveAndCacheRepositoryAccess;
    }

    @Override
    public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier() {
        return delegate.getComponentMetadataSupplier();
    }

    private class LocateInCacheRepositoryAccess implements ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> {
        @Override
        public String toString() {
            return "cache lookup for " + delegate;
        }

        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            // First try to determine the versions in-memory: don't use the cache in this case
            delegate.getLocalAccess().listModuleVersions(dependency, result);
            if (result.hasResult()) {
                return;
            }

            listModuleVersionsFromCache(dependency, result);
        }

        private void listModuleVersionsFromCache(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            ModuleComponentSelector requested = dependency.getSelector();
            final ModuleIdentifier moduleId = requested.getModuleIdentifier();
            ModuleVersionsCache.CachedModuleVersionList cachedModuleVersionList = moduleVersionsCache.getCachedModuleResolution(delegate, moduleId);
            if (cachedModuleVersionList != null) {
                Set<String> versionList = cachedModuleVersionList.getModuleVersions();
                Set<ModuleVersionIdentifier> versions = versionList
                    .stream()
                    .map(original -> DefaultModuleVersionIdentifier.newId(moduleId, original))
                    .collect(Collectors.toSet());
                Expiry expiry = cachePolicy.versionListExpiry(moduleId, versions, cachedModuleVersionList.getAge());
                if (expiry.isMustCheck()) {
                    LOGGER.debug("Version listing in dynamic revision cache is expired: will perform fresh resolve of '{}' in '{}'", requested, delegate.getName());
                } else {
                    // When age == 0, verified since the start of this build, assume listing hasn't changed
                    boolean authoritative = cachedModuleVersionList.getAge().toMillis() == 0;
                    result.listed(versionList);
                    result.setAuthoritative(authoritative);
                    listener.onDynamicVersionSelection(requested, expiry, versions);
                }
            }
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            // First try to determine the metadata in-memory: don't use the cache in this case
            DefaultBuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> localResult = new DefaultBuildableModuleComponentMetaDataResolveResult<>();
            delegate.getLocalAccess().resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, localResult);
            if (localResult.hasResult()) {
                localResult.applyTo(result, resolveStateFactory::stateFor);
                return;
            }


            resolveComponentMetaDataFromCache(moduleComponentIdentifier, requestMetaData, result);
        }

        private void resolveComponentMetaDataFromCache(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            ModuleMetadataCache.CachedMetadata cachedMetadata = moduleMetadataCache.getCachedModuleDescriptor(delegate, moduleComponentIdentifier);
            if (cachedMetadata == null) {
                return;
            }
            if (cachedMetadata.isMissing()) {
                if (cachePolicy.missingModuleExpiry(moduleComponentIdentifier, cachedMetadata.getAge()).isMustCheck()) {
                    LOGGER.debug("Cached meta-data for missing module is expired: will perform fresh resolve of '{}' in '{}'", moduleComponentIdentifier, delegate.getName());
                    return;
                }
                LOGGER.debug("Detected non-existence of module '{}' in resolver cache '{}'", moduleComponentIdentifier, delegate.getName());
                result.missing();
                // When age == 0, verified since the start of this build, assume still missing
                result.setAuthoritative(cachedMetadata.getAge().toMillis() == 0);
                return;
            }
            ModuleComponentGraphResolveState state = getProcessedMetadata(metadataProcessor.getRulesHash(), cachedMetadata);
            if (requestMetaData.isChanging() || state.getMetadata().isChanging()) {
                Expiry expiry = cachePolicy.changingModuleExpiry(moduleComponentIdentifier, cachedMetadata.getModuleVersion(), cachedMetadata.getAge());
                if (expiry.isMustCheck()) {
                    LOGGER.debug("Cached meta-data for changing module is expired: will perform fresh resolve of '{}' in '{}'", moduleComponentIdentifier, delegate.getName());
                    return;
                }
                LOGGER.debug("Found cached version of changing module '{}' in '{}'", moduleComponentIdentifier, delegate.getName());
                listener.onChangingModuleResolve(moduleComponentIdentifier, expiry);
            } else {
                if (cachePolicy.moduleExpiry(moduleComponentIdentifier, cachedMetadata.getModuleVersion(), cachedMetadata.getAge()).isMustCheck()) {
                    LOGGER.debug("Cached meta-data for module must be refreshed: will perform fresh resolve of '{}' in '{}'", moduleComponentIdentifier, delegate.getName());
                    return;
                }
            }

            LOGGER.debug("Using cached module metadata for module '{}' in '{}'", moduleComponentIdentifier, delegate.getName());
            result.resolved(state);
            // When age == 0, verified since the start of this build, assume the meta-data hasn't changed
            result.setAuthoritative(cachedMetadata.getAge().toMillis() == 0);
        }

        private ModuleComponentGraphResolveState getProcessedMetadata(int key, ModuleMetadataCache.CachedMetadata cachedMetadata) {
            ModuleComponentGraphResolveState state = cachedMetadata.getProcessedMetadata(key);
            if (state == null) {
                ModuleComponentResolveMetadata metadata = metadataProcessor.processMetadata(cachedMetadata.getMetadata());
                metadata = attachRepositorySource(metadata);
                state = resolveStateFactory.stateFor(metadata);
                // Save the processed metadata for next time.
                cachedMetadata.putProcessedMetadata(key, state);
            }
            return state;
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            // First try to determine the artifacts in-memory (e.g using the metadata): don't use the cache in this case
            delegate.getLocalAccess().resolveArtifactsWithType(component, artifactType, result);
            if (result.hasResult()) {
                return;
            }

            resolveModuleArtifactsFromCache(cacheKey(artifactType), component, result);
        }

        private void resolveModuleArtifactsFromCache(String contextId, ComponentArtifactResolveMetadata component, BuildableArtifactSetResolveResult result) {
            CachedArtifacts cachedModuleArtifacts = moduleArtifactsCache.getCachedArtifacts(delegate, component.getId(), contextId);
            ModuleSources sources = component.getSources();
            ModuleDescriptorHashModuleSource cachingModuleSource = findCachingModuleSource(sources);
            HashCode moduleDescriptorHash = cachingModuleSource.getDescriptorHash();

            if (cachedModuleArtifacts != null) {
                if (!cachePolicy.moduleArtifactsExpiry(component.getModuleVersionId(), null, Duration.ofMillis(cachedModuleArtifacts.getAgeMillis()),
                    cachingModuleSource.isChangingModule(), moduleDescriptorHash.equals(cachedModuleArtifacts.getDescriptorHash())).isMustCheck()) {
                    result.resolved(cachedModuleArtifacts.getArtifacts());
                    return;
                }

                LOGGER.debug("Artifact listing has expired: will perform fresh resolve of '{}' for '{}' in '{}'", contextId, component.getModuleVersionId(), delegate.getName());
            }
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            // First try to resolve the artifact in-memory (e.g using the metadata): don't use the cache in this case
            delegate.getLocalAccess().resolveArtifact(artifact, moduleSources, result);
            if (result.hasResult()) {
                return;
            }

            resolveArtifactFromCache(artifact, moduleSources, result);
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            ModuleMetadataCache.CachedMetadata cachedMetadata = moduleMetadataCache.getCachedModuleDescriptor(delegate, moduleComponentIdentifier);
            if (cachedMetadata == null) {
                return estimateCostViaRemoteAccess(moduleComponentIdentifier);
            }
            if (cachedMetadata.isMissing()) {
                if (cachePolicy.missingModuleExpiry(moduleComponentIdentifier, cachedMetadata.getAge()).isMustCheck()) {
                    return estimateCostViaRemoteAccess(moduleComponentIdentifier);
                }
                return MetadataFetchingCost.CHEAP;
            }
            ModuleComponentGraphResolveState state = getProcessedMetadata(metadataProcessor.getRulesHash(), cachedMetadata);
            if (state.getMetadata().isChanging()) {
                if (cachePolicy.changingModuleExpiry(moduleComponentIdentifier, cachedMetadata.getModuleVersion(), cachedMetadata.getAge()).isMustCheck()) {
                    return estimateCostViaRemoteAccess(moduleComponentIdentifier);
                }
            } else {
                if (cachePolicy.moduleExpiry(moduleComponentIdentifier, cachedMetadata.getModuleVersion(), cachedMetadata.getAge()).isMustCheck()) {
                    return estimateCostViaRemoteAccess(moduleComponentIdentifier);
                }
            }
            return MetadataFetchingCost.FAST;
        }

        private MetadataFetchingCost estimateCostViaRemoteAccess(ModuleComponentIdentifier moduleComponentIdentifier) {
            return delegate.getRemoteAccess().estimateMetadataFetchingCost(moduleComponentIdentifier);
        }

        private void resolveArtifactFromCache(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            CachedArtifact cached = moduleArtifactCache.lookup(artifactCacheKey(artifact.getId()));
            if (cached != null) {
                ModuleDescriptorHashModuleSource moduleSource = findCachingModuleSource(moduleSources);
                final HashCode descriptorHash = moduleSource.getDescriptorHash();
                Duration age = Duration.ofMillis(timeProvider.getCurrentTime() - cached.getCachedAt());
                final boolean isChangingModule = moduleSource.isChangingModule();
                ModuleComponentArtifactMetadata moduleComponentArtifactMetadata = (ModuleComponentArtifactMetadata) artifact;
                if (cached.isMissing()) {
                    Expiry expiry = cachePolicy.artifactExpiry(moduleComponentArtifactMetadata, null, age, isChangingModule, descriptorHash.equals(cached.getDescriptorHash()));
                    if (!expiry.isMustCheck()) {
                        LOGGER.debug("Detected non-existence of artifact '{}' in resolver cache", artifact);
                        for (String location : cached.attemptedLocations()) {
                            result.attempted(location);
                        }
                        result.notFound(artifact.getId());
                    }
                } else {
                    File cachedArtifactFile = cached.getCachedFile();
                    Expiry expiry = cachePolicy.artifactExpiry(moduleComponentArtifactMetadata, cachedArtifactFile, age, isChangingModule, descriptorHash.equals(cached.getDescriptorHash()));
                    if (!expiry.isMustCheck()) {
                        LOGGER.debug("Found artifact '{}' in resolver cache: {}", artifact, cachedArtifactFile);
                        result.resolved(cachedArtifactFile);
                    }
                }
            }
        }
    }

    private static ModuleDescriptorHashModuleSource findCachingModuleSource(ModuleSources sources) {
        return sources.getSource(ModuleDescriptorHashModuleSource.class)
               .orElseThrow(() -> new RuntimeException("Cannot find expected module source " + ModuleDescriptorHashModuleSource.class.getSimpleName() + " in " + sources));
    }

    private class ResolveAndCacheRepositoryAccess implements ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> {
        @Override
        public String toString() {
            return "cache > " + delegate.getRemoteAccess();
        }

        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            delegate.getRemoteAccess().listModuleVersions(dependency, result);
            switch (result.getState()) {
                case Listed:
                    ModuleIdentifier moduleId = dependency.getSelector().getModuleIdentifier();
                    Set<String> versionList = result.getVersions();
                    Set<ModuleVersionIdentifier> versions = versionList
                        .stream()
                        .map(original -> DefaultModuleVersionIdentifier.newId(moduleId, original))
                        .collect(Collectors.toSet());
                    moduleVersionsCache.cacheModuleVersionList(delegate, moduleId, versionList);
                    listener.onDynamicVersionSelection(
                        dependency.getSelector(),
                        cachePolicy.versionListExpiry(moduleId, versions, Duration.ZERO),
                        versions
                    );
                    break;
                case Failed:
                    break;
                default:
                    throw new IllegalStateException("Unexpected state on listModuleVersions: " + result.getState());
            }
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            resolveComponentMetaDataAndCache(moduleComponentIdentifier, requestMetaData, result);
        }

        private void resolveComponentMetaDataAndCache(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            ComponentOverrideMetadata forced = requestMetaData.withChanging();
            DefaultBuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> localResult = new DefaultBuildableModuleComponentMetaDataResolveResult<>();
            delegate.getRemoteAccess().resolveComponentMetaData(moduleComponentIdentifier, forced, localResult);
            switch (localResult.getState()) {
                case Missing:
                    moduleMetadataCache.cacheMissing(delegate, moduleComponentIdentifier);
                    localResult.applyTo(result, metadata -> {
                        // Should not be called
                        throw new IllegalStateException();
                    });
                    break;
                case Resolved:
                    ModuleComponentResolveMetadata resolvedMetadata = localResult.getMetaData();
                    ModuleMetadataCache.CachedMetadata cachedMetadata = moduleMetadataCache.cacheMetaData(delegate, moduleComponentIdentifier, resolvedMetadata);
                    // Starting here we're going to process the component metadata rules
                    // Therefore metadata can be mutated, and will _not_ be stored in the module metadata cache
                    // but will be in the _in memory_ cache
                    ModuleComponentResolveMetadata processedMetadata = metadataProcessor.processMetadata(resolvedMetadata);
                    processedMetadata = attachRepositorySource(processedMetadata);
                    if (processedMetadata.isChanging() || requestMetaData.isChanging()) {
                        processedMetadata = makeChanging(processedMetadata);
                        Expiry expiry = cachePolicy.changingModuleExpiry(moduleComponentIdentifier, cachedMetadata.getModuleVersion(), Duration.ZERO);
                        listener.onChangingModuleResolve(moduleComponentIdentifier, expiry);
                    }
                    ModuleComponentGraphResolveState state = resolveStateFactory.stateFor(processedMetadata);
                    cachedMetadata.putProcessedMetadata(metadataProcessor.getRulesHash(), state);
                    localResult.applyTo(result);
                    result.resolved(state);
                    break;
                case Failed:
                    localResult.applyTo(result, metadata -> {
                        // should not be called
                        throw new IllegalStateException();
                    });
                    break;
                default:
                    throw new IllegalStateException("Unexpected resolve state: " + result.getState());
            }
        }

        private ModuleComponentResolveMetadata makeChanging(ModuleComponentResolveMetadata processedMetadata) {
            MutableModuleSources sources = new MutableModuleSources();
            processedMetadata.getSources().withSources(src -> {
                if (src instanceof ModuleDescriptorHashModuleSource) {
                    ModuleDescriptorHashModuleSource changingSource = new ModuleDescriptorHashModuleSource(
                        ((ModuleDescriptorHashModuleSource) src).getDescriptorHash(),
                        true
                    );
                    sources.add(changingSource);
                } else {
                    sources.add(src);
                }
            });
            return processedMetadata.withSources(sources);
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            delegate.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result);

            if (result.getFailure() == null) {
                ModuleDescriptorHashModuleSource moduleSource = findCachingModuleSource(component.getSources());
                moduleArtifactsCache.cacheArtifacts(delegate, component.getId(), cacheKey(artifactType), moduleSource.getDescriptorHash(), result.getResult());
            }
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {

            delegate.getRemoteAccess().resolveArtifact(artifact, moduleSources, result);
            LOGGER.debug("Downloaded artifact '{}' from resolver: {}", artifact, delegate.getName());

            ArtifactResolveException failure = result.getFailure();
            ModuleDescriptorHashModuleSource cachingModuleSource = findCachingModuleSource(moduleSources);
            if (failure == null) {
                moduleArtifactCache.store(artifactCacheKey(artifact.getId()), result.getResult(), cachingModuleSource.getDescriptorHash());
            } else if (failure instanceof ArtifactNotFoundException) {
                moduleArtifactCache.storeMissing(artifactCacheKey(artifact.getId()), result.getAttempted(), cachingModuleSource.getDescriptorHash());
            }
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return delegate.getLocalAccess().estimateMetadataFetchingCost(moduleComponentIdentifier);
        }
    }

    private ModuleComponentResolveMetadata attachRepositorySource(ModuleComponentResolveMetadata processedMetadata) {
        RepositoryChainModuleSource moduleSource = new RepositoryChainModuleSource(delegate);
        ModuleSources originSources = processedMetadata.getSources();
        ImmutableModuleSources mergedSources = ImmutableModuleSources.of(originSources, moduleSource);
        processedMetadata = processedMetadata.withSources(mergedSources);
        return processedMetadata;
    }

    private String cacheKey(ArtifactType artifactType) {
        return "artifacts:" + artifactType.name();
    }

    private ArtifactAtRepositoryKey artifactCacheKey(ComponentArtifactIdentifier id) {
        return new ArtifactAtRepositoryKey(delegate.getId(), id);
    }

}
