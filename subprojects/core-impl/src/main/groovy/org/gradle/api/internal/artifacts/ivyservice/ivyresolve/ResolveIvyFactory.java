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
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache.InMemoryDependencyMetadataCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.internal.TimeProvider;
import org.gradle.util.WrapUtil;

public class ResolveIvyFactory {
    private final IvyFactory ivyFactory;
    private final SettingsConverter settingsConverter;
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private final CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex;
    private final CacheLockingManager cacheLockingManager;
    private final StartParameterResolutionOverride startParameterResolutionOverride;
    private final TimeProvider timeProvider;
    private InMemoryDependencyMetadataCache inMemoryCache;

    public ResolveIvyFactory(IvyFactory ivyFactory, SettingsConverter settingsConverter,
                             ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache,
                             CachedArtifactIndex artifactAtRepositoryCachedResolutionIndex,
                             CacheLockingManager cacheLockingManager, StartParameterResolutionOverride startParameterResolutionOverride,
                             TimeProvider timeProvider, InMemoryDependencyMetadataCache inMemoryCache) {
        this.ivyFactory = ivyFactory;
        this.settingsConverter = settingsConverter;
        this.moduleResolutionCache = moduleResolutionCache;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.artifactAtRepositoryCachedResolutionIndex = artifactAtRepositoryCachedResolutionIndex;
        this.cacheLockingManager = cacheLockingManager;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
        this.timeProvider = timeProvider;
        this.inMemoryCache = inMemoryCache;
    }

    public IvyAdapter create(ConfigurationInternal configuration, Iterable<? extends ResolutionAwareRepository> repositories) {
        UserResolverChain userResolverChain = new UserResolverChain();
        ResolutionRules resolutionRules = configuration.getResolutionStrategy().getResolutionRules();
        startParameterResolutionOverride.addResolutionRules(resolutionRules);

        LoopbackDependencyResolver loopbackDependencyResolver = new LoopbackDependencyResolver(SettingsConverter.LOOPBACK_RESOLVER_NAME, userResolverChain, cacheLockingManager);

        IvySettings ivySettings = settingsConverter.convertForResolve(loopbackDependencyResolver);
        userResolverChain.setSettings(ivySettings);

        Ivy ivy = ivyFactory.createIvy(ivySettings);
        ResolveData resolveData = createResolveData(ivy, configuration.getName());
        IvyContextualiser contextualiser = new IvyContextualiser(ivy, resolveData);

        for (ResolutionAwareRepository repository : repositories) {
            IvyAwareModuleVersionRepository moduleVersionRepository = repository.createResolver();
            moduleVersionRepository.setSettings(ivySettings);

            LocalAwareModuleVersionRepository localAwareRepository;
            if (moduleVersionRepository.isLocal()) {
                localAwareRepository = new LocalModuleVersionRepository(moduleVersionRepository);
            } else {
                ModuleVersionRepository wrapperRepository = new CacheLockingModuleVersionRepository(moduleVersionRepository, cacheLockingManager);
                wrapperRepository = startParameterResolutionOverride.overrideModuleVersionRepository(wrapperRepository);
                localAwareRepository = new CachingModuleVersionRepository(wrapperRepository, moduleResolutionCache, moduleDescriptorCache, artifactAtRepositoryCachedResolutionIndex,
                        configuration.getResolutionStrategy().getCachePolicy(), timeProvider);
            }
            if (moduleVersionRepository.isDynamicResolveMode()) {
                localAwareRepository = new IvyDynamicResolveModuleVersionRepository(localAwareRepository);
            }
            localAwareRepository = contextualiser.contextualise(LocalAwareModuleVersionRepository.class, localAwareRepository);
            localAwareRepository = inMemoryCache.cached(localAwareRepository);
            userResolverChain.add(localAwareRepository);
        }

        return new DefaultIvyAdapter(resolveData, userResolverChain);
    }

    private ResolveData createResolveData(Ivy ivy, String configurationName) {
        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configurationName));
        return new ResolveData(ivy.getResolveEngine(), options);
    }
}
