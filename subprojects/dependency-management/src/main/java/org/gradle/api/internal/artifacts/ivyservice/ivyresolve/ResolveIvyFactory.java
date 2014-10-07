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

import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryCachedRepositoryFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resource.cached.CachedArtifactIndex;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Collection;

public class ResolveIvyFactory {
    private final ModuleVersionsCache moduleVersionsCache;
    private final ModuleMetaDataCache moduleMetaDataCache;
    private final ModuleArtifactsCache moduleArtifactsCache;
    private final CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex;
    private final CacheLockingManager cacheLockingManager;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final BuildCommencedTimeProvider timeProvider;
    private final InMemoryCachedRepositoryFactory inMemoryCache;
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;

    public ResolveIvyFactory(ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache, ModuleArtifactsCache moduleArtifactsCache,
                             CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex,
                             CacheLockingManager cacheLockingManager, StartParameterResolutionOverride startParameterResolutionOverride,
                             BuildCommencedTimeProvider timeProvider, InMemoryCachedRepositoryFactory inMemoryCache, VersionMatcher versionMatcher, LatestStrategy latestStrategy) {
        this.moduleVersionsCache = moduleVersionsCache;
        this.moduleMetaDataCache = moduleMetaDataCache;
        this.moduleArtifactsCache = moduleArtifactsCache;
        this.artifactAtRepositoryCachedResolutionIndex = artifactAtRepositoryCachedResolutionIndex;
        this.cacheLockingManager = cacheLockingManager;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.timeProvider = timeProvider;
        this.inMemoryCache = inMemoryCache;
        this.versionMatcher = versionMatcher;
        this.latestStrategy = latestStrategy;
    }

    public RepositoryChain create(ConfigurationInternal configuration,
                                  Collection<? extends ResolutionAwareRepository> repositories,
                                  ComponentMetadataProcessor metadataProcessor) {
        if (repositories.isEmpty()) {
            return new NoRepositoriesResolver();
        }

        ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
        ResolutionRules resolutionRules = resolutionStrategy.getResolutionRules();
        CachePolicy cachePolicy = resolutionStrategy.getCachePolicy();

        startParameterResolutionOverride.addResolutionRules(resolutionRules);

        UserResolverChain userResolverChain = new UserResolverChain(versionMatcher, latestStrategy, resolutionStrategy.getComponentSelection());
        RepositoryChain parentLookupResolver = new ParentModuleLookupResolver(userResolverChain, cacheLockingManager);

        for (ResolutionAwareRepository repository : repositories) {
            ConfiguredModuleComponentRepository baseRepository = repository.createResolver();

            if (baseRepository instanceof ExternalResourceResolver) {
                ((ExternalResourceResolver) baseRepository).setRepositoryChain(parentLookupResolver);
            }

            // TODO:DAZ In theory we could update this so that _all_ repositories are wrapped in a cache:
            //     - would need to add local/remote pattern to artifact download
            //     - This might help later when we integrate in-memory caching with file-backed caching.
            ModuleComponentRepository moduleComponentRepository = baseRepository;
            if (baseRepository.isLocal()) {
                moduleComponentRepository = new LocalModuleComponentRepository(baseRepository, metadataProcessor);
            } else {
                moduleComponentRepository = new CacheLockReleasingModuleComponentsRepository(moduleComponentRepository, cacheLockingManager);
                moduleComponentRepository = startParameterResolutionOverride.overrideModuleVersionRepository(moduleComponentRepository);
                moduleComponentRepository = new CachingModuleComponentRepository(moduleComponentRepository, moduleVersionsCache, moduleMetaDataCache, moduleArtifactsCache, artifactAtRepositoryCachedResolutionIndex,
                        cachePolicy, timeProvider, metadataProcessor);
            }

            if (baseRepository.isDynamicResolveMode()) {
                moduleComponentRepository = IvyDynamicResolveModuleComponentRepositoryAccess.wrap(moduleComponentRepository);
            }
            moduleComponentRepository = inMemoryCache.cached(moduleComponentRepository);
            userResolverChain.add(moduleComponentRepository);
        }

        return userResolverChain;
    }

    /**
     * Provides access to the top-level resolver chain for looking up parent modules when parsing module descriptor files.
     */
    private static class ParentModuleLookupResolver implements RepositoryChain, DependencyToComponentResolver, ArtifactResolver {
        private final DependencyToComponentResolver dependencyResolver;
        private final ArtifactResolver artifactResolver;
        private final CacheLockingManager cacheLockingManager;

        public ParentModuleLookupResolver(RepositoryChain repositoryChain, CacheLockingManager cacheLockingManager) {
            this.dependencyResolver = repositoryChain.getDependencyResolver();
            this.artifactResolver = repositoryChain.getArtifactResolver();
            this.cacheLockingManager = cacheLockingManager;
        }

        public ComponentMetaDataResolver getComponentMetaDataResolver() {
            throw new UnsupportedOperationException();
        }

        public DependencyToComponentIdResolver getComponentIdResolver() {
            throw new UnsupportedOperationException();
        }

        public ArtifactResolver getArtifactResolver() {
            return this;
        }

        public DependencyToComponentResolver getDependencyResolver() {
            return this;
        }

        public void resolve(final DependencyMetaData dependency, final BuildableComponentResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s", dependency), new Runnable() {
                public void run() {
                    dependencyResolver.resolve(dependency, result);
                }
            });
        }

        public void resolveModuleArtifacts(final ComponentResolveMetaData component, final ArtifactType artifactType, final BuildableArtifactSetResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s for %s", artifactType, component), new Runnable() {
                public void run() {
                    artifactResolver.resolveModuleArtifacts(component, artifactType, result);
                }
            });
        }

        public void resolveModuleArtifacts(final ComponentResolveMetaData component, final ComponentUsage usage, final BuildableArtifactSetResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s for %s", usage, component), new Runnable() {
                public void run() {
                    artifactResolver.resolveModuleArtifacts(component, usage, result);
                }
            });
        }

        public void resolveArtifact(final ComponentArtifactMetaData artifact, final ModuleSource moduleSource, final BuildableArtifactResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s", artifact), new Runnable() {
                public void run() {
                    artifactResolver.resolveArtifact(artifact, moduleSource, result);
                }
            });
        }
    }
}
