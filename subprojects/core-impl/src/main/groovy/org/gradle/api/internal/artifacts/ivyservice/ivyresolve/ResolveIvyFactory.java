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
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleMetaDataCache;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.util.BuildCommencedTimeProvider;
import org.gradle.util.WrapUtil;

public class ResolveIvyFactory {
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleMetaDataCache moduleMetaDataCache;
    private final CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex;
    private final CacheLockingManager cacheLockingManager;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final BuildCommencedTimeProvider timeProvider;
    private final InMemoryDependencyMetadataCache inMemoryCache;
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;

    public ResolveIvyFactory(ModuleResolutionCache moduleResolutionCache, ModuleMetaDataCache moduleMetaDataCache,
                             CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex,
                             CacheLockingManager cacheLockingManager, StartParameterResolutionOverride startParameterResolutionOverride,
                             BuildCommencedTimeProvider timeProvider, InMemoryDependencyMetadataCache inMemoryCache, VersionMatcher versionMatcher, LatestStrategy latestStrategy) {
        this.moduleResolutionCache = moduleResolutionCache;
        this.moduleMetaDataCache = moduleMetaDataCache;
        this.artifactAtRepositoryCachedResolutionIndex = artifactAtRepositoryCachedResolutionIndex;
        this.cacheLockingManager = cacheLockingManager;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.timeProvider = timeProvider;
        this.inMemoryCache = inMemoryCache;
        this.versionMatcher = versionMatcher;
        this.latestStrategy = latestStrategy;
    }

    public DependencyToModuleVersionResolver create(ConfigurationInternal configuration, Iterable<? extends ResolutionAwareRepository> repositories) {
        ResolutionRules resolutionRules = configuration.getResolutionStrategy().getResolutionRules();
        startParameterResolutionOverride.addResolutionRules(resolutionRules);

        UserResolverChain userResolverChain = new UserResolverChain(versionMatcher, latestStrategy);
        DependencyToModuleVersionResolver parentLookupResolver = new ParentModuleLookupResolver(userResolverChain, cacheLockingManager);

        for (ResolutionAwareRepository repository : repositories) {
            ConfiguredModuleVersionRepository moduleVersionRepository = repository.createResolver();

            if (moduleVersionRepository instanceof IvyAwareModuleVersionRepository) {
                ivyContextualize((IvyAwareModuleVersionRepository) moduleVersionRepository, userResolverChain, configuration.getName());
            }
            if (moduleVersionRepository instanceof ExternalResourceResolver) {
                // TODO:DAZ Should have type for this
                ((ExternalResourceResolver) moduleVersionRepository).setResolver(parentLookupResolver);
            }

            LocalAwareModuleVersionRepository localAwareRepository;
            if (moduleVersionRepository.isLocal()) {
                localAwareRepository = new LocalModuleVersionRepository(moduleVersionRepository);
            } else {
                ModuleVersionRepository wrapperRepository = new CacheLockingModuleVersionRepository(moduleVersionRepository, cacheLockingManager);
                wrapperRepository = startParameterResolutionOverride.overrideModuleVersionRepository(wrapperRepository);
                localAwareRepository = new CachingModuleVersionRepository(wrapperRepository, moduleResolutionCache, moduleMetaDataCache, artifactAtRepositoryCachedResolutionIndex,
                        parentLookupResolver, configuration.getResolutionStrategy().getCachePolicy(), timeProvider);
            }
            if (moduleVersionRepository.isDynamicResolveMode()) {
                localAwareRepository = new IvyDynamicResolveModuleVersionRepository(localAwareRepository);
            }
            localAwareRepository = inMemoryCache.cached(localAwareRepository);
            userResolverChain.add(localAwareRepository);
        }

        return userResolverChain;
    }

    private void ivyContextualize(IvyAwareModuleVersionRepository ivyAwareRepository, UserResolverChain userResolverChain, String configurationName) {
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

    /**
     * Provides access to the top-level resolver chain for looking up parent modules when parsing module descriptor files.
     */
    private static class ParentModuleLookupResolver implements DependencyToModuleVersionResolver {
        private final UserResolverChain delegate;
        private final CacheLockingManager cacheLockingManager;

        public ParentModuleLookupResolver(UserResolverChain delegate, CacheLockingManager cacheLockingManager) {
            this.delegate = delegate;
            this.cacheLockingManager = cacheLockingManager;
        }

        public void resolve(final DependencyMetaData dependency, final BuildableModuleVersionResolveResult result) {
            cacheLockingManager.useCache(String.format("Resolve %s", dependency), new Runnable() {
                public void run() {
                    delegate.resolve(dependency, result);
                }
            });
        }
    }
}
