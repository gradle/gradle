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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.WrapUtil;

public class ResolveIvyFactory {
    private final ModuleVersionsCache moduleVersionsCache;
    private final ModuleMetaDataCache moduleMetaDataCache;
    private final ModuleArtifactsCache moduleArtifactsCache;
    private final CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex;
    private final CacheLockingManager cacheLockingManager;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final BuildCommencedTimeProvider timeProvider;
    private final InMemoryDependencyMetadataCache inMemoryCache;
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;

    public ResolveIvyFactory(ModuleVersionsCache moduleVersionsCache, ModuleMetaDataCache moduleMetaDataCache, ModuleArtifactsCache moduleArtifactsCache,
                             CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex,
                             CacheLockingManager cacheLockingManager, StartParameterResolutionOverride startParameterResolutionOverride,
                             BuildCommencedTimeProvider timeProvider, InMemoryDependencyMetadataCache inMemoryCache, VersionMatcher versionMatcher, LatestStrategy latestStrategy) {
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
                                  Iterable<? extends ResolutionAwareRepository> repositories,
                                  ModuleMetadataProcessor metadataProcessor) {
        ResolutionRules resolutionRules = configuration.getResolutionStrategy().getResolutionRules();
        CachePolicy cachePolicy = configuration.getResolutionStrategy().getCachePolicy();

        startParameterResolutionOverride.addResolutionRules(resolutionRules);

        UserResolverChain userResolverChain = new UserResolverChain(versionMatcher, latestStrategy);
        RepositoryChain parentLookupResolver = new ParentModuleLookupResolver(userResolverChain, cacheLockingManager);

        for (ResolutionAwareRepository repository : repositories) {
            ConfiguredModuleVersionRepository moduleVersionRepository = repository.createResolver();

            if (moduleVersionRepository instanceof IvyAwareModuleVersionRepository) {
                ivyContextualize((IvyAwareModuleVersionRepository) moduleVersionRepository, userResolverChain, configuration.getName());
            }
            if (moduleVersionRepository instanceof ExternalResourceResolver) {
                ((ExternalResourceResolver) moduleVersionRepository).setRepositoryChain(parentLookupResolver);
            }

            LocalAwareModuleVersionRepository localAwareRepository;
            if (moduleVersionRepository.isLocal()) {
                localAwareRepository = new LocalModuleVersionRepository(moduleVersionRepository, metadataProcessor);
            } else {
                LocalArtifactsModuleVersionRepository wrapperRepository = new CacheLockingModuleVersionRepository(moduleVersionRepository, cacheLockingManager);
                wrapperRepository = startParameterResolutionOverride.overrideModuleVersionRepository(wrapperRepository);
                localAwareRepository = new CachingModuleVersionRepository(wrapperRepository, moduleVersionsCache, moduleMetaDataCache, moduleArtifactsCache, artifactAtRepositoryCachedResolutionIndex,
                        cachePolicy, timeProvider, metadataProcessor, getModuleExtractor(moduleVersionRepository));
            }
            if (moduleVersionRepository.isDynamicResolveMode()) {
                localAwareRepository = new IvyDynamicResolveModuleVersionRepository(localAwareRepository);
            }
            localAwareRepository = inMemoryCache.cached(localAwareRepository);
            userResolverChain.add(localAwareRepository);
        }

        return userResolverChain;
    }

    private void ivyContextualize(IvyAwareModuleVersionRepository ivyAwareRepository, RepositoryChain userResolverChain, String configurationName) {
        Ivy ivy = IvyContext.getContext().getIvy();
        IvySettings ivySettings = ivy.getSettings();
        LoopbackDependencyResolver loopbackDependencyResolver = new LoopbackDependencyResolver("main", userResolverChain, cacheLockingManager);
        ivySettings.addResolver(loopbackDependencyResolver);
        ivySettings.setDefaultResolver(loopbackDependencyResolver.getName());

        ResolveData resolveData = createResolveData(ivy, configurationName);
        ivyAwareRepository.setSettings(ivySettings);
        ivyAwareRepository.setResolveData(resolveData);
    }

    private ResolveData createResolveData(Ivy ivy, String configurationName) {
        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configurationName));
        return new ResolveData(ivy.getResolveEngine(), options);
    }

    private Transformer<ModuleIdentifier, ModuleVersionSelector> getModuleExtractor(ConfiguredModuleVersionRepository rootRepository) {
        // If the backing repository is a custom ivy resolver, then we don't get a full listing
        if (rootRepository instanceof IvyAwareModuleVersionRepository) {
            return new LegacyIvyResolverModuleIdExtractor();
        }
        return new DefaultModuleIdExtractor();
    }

    /**
     * Provides access to the top-level resolver chain for looking up parent modules when parsing module descriptor files.
     */
    private static class ParentModuleLookupResolver implements RepositoryChain, DependencyToModuleVersionResolver, ArtifactResolver {
        private final DependencyToModuleVersionResolver dependencyResolver;
        private final ArtifactResolver artifactResolver;
        private final CacheLockingManager cacheLockingManager;

        public ParentModuleLookupResolver(RepositoryChain repositoryChain, CacheLockingManager cacheLockingManager) {
            this.dependencyResolver = repositoryChain.getDependencyResolver();
            this.artifactResolver = repositoryChain.getArtifactResolver();
            this.cacheLockingManager = cacheLockingManager;
        }

        public ArtifactResolver getArtifactResolver() {
            return this;
        }

        public DependencyToModuleVersionResolver getDependencyResolver() {
            return this;
        }

        public void resolve(final DependencyMetaData dependency, final BuildableComponentResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s", dependency), new Runnable() {
                public void run() {
                    dependencyResolver.resolve(dependency, result);
                }
            });
        }

        public void resolveModuleArtifacts(final ComponentMetaData component, final ArtifactResolveContext context, final BuildableArtifactSetResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s for %s", context.getDescription(), component), new Runnable() {
                public void run() {
                    artifactResolver.resolveModuleArtifacts(component, context, result);
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

    private static class DefaultModuleIdExtractor implements Transformer<ModuleIdentifier, ModuleVersionSelector> {
        public ModuleIdentifier transform(ModuleVersionSelector original) {
            return new DefaultModuleIdentifier(original.getGroup(), original.getName());
        }
    }

    // When caching module listing for ivy resolvers, we can only cache for a particular version request
    // TODO: Remove this when we remove support for Ivy DependencyResolvers
    private static class LegacyIvyResolverModuleIdExtractor implements Transformer<ModuleIdentifier, ModuleVersionSelector> {
        public ModuleIdentifier transform(ModuleVersionSelector original) {
            return new DefaultModuleIdentifier(original.getGroup(), original.getName() + ":" + original.getVersion());
        }
    }
}
