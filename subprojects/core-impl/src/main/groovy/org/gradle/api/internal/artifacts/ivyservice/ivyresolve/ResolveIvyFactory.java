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
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter;
import org.gradle.api.internal.artifacts.ivyservice.filestore.ArtifactFileStore;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.util.WrapUtil;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.util.List;

public class ResolveIvyFactory {
    private final IvyFactory ivyFactory;
    private final ResolverProvider resolverProvider;
    private final SettingsConverter settingsConverter;
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private final ArtifactResolutionCache artifactResolutionCache;
    private final ArtifactFileStore artifactFileStore;
    private final CacheLockingManager cacheLockingManager;
    private final ResolveModeOverride resolveModeOverride;

    public ResolveIvyFactory(IvyFactory ivyFactory, ResolverProvider resolverProvider, SettingsConverter settingsConverter, 
                             ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache, 
                             ArtifactResolutionCache artifactResolutionCache, ArtifactFileStore artifactFileStore,
                             CacheLockingManager cacheLockingManager, ResolveModeOverride resolveModeOverride) {
        this.ivyFactory = ivyFactory;
        this.resolverProvider = resolverProvider;
        this.settingsConverter = settingsConverter;
        this.moduleResolutionCache = moduleResolutionCache;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.artifactResolutionCache = artifactResolutionCache;
        this.artifactFileStore = artifactFileStore;
        this.cacheLockingManager = cacheLockingManager;
        this.resolveModeOverride = resolveModeOverride;
    }

    public IvyAdapter create(ConfigurationInternal configuration) {
        UserResolverChain userResolverChain = new UserResolverChain();
        CachePolicy cachePolicy = configuration.getResolutionStrategy().getCachePolicy();
        cachePolicy = resolveModeOverride.overrideCachePolicy(cachePolicy);

        LoopbackDependencyResolver loopbackDependencyResolver = new LoopbackDependencyResolver(SettingsConverter.LOOPBACK_RESOLVER_NAME, userResolverChain, cacheLockingManager);
        List<DependencyResolver> rawResolvers = resolverProvider.getResolvers();

        // Unfortunately, WharfResolverMetadata requires the resolver to have settings to calculate an id.
        // We then need to set the ivySettings on the delegating resolver as well
        IvySettings ivySettings = settingsConverter.convertForResolve(loopbackDependencyResolver, rawResolvers);
        Ivy ivy = ivyFactory.createIvy(ivySettings);
        ResolveData resolveData = createResolveData(ivy, configuration.getName());

        IvyContextualiser contextualiser = new IvyContextualiser(ivy, resolveData);
        for (DependencyResolver rawResolver : rawResolvers) {
            String resolverId = new WharfResolverMetadata(rawResolver).getId();

            CacheLockingDependencyResolver cacheLockingResolver = new CacheLockingDependencyResolver(rawResolver, cacheLockingManager);
            cacheLockingResolver.setSettings(ivySettings);

            ModuleVersionRepository moduleVersionRepository = new DependencyResolverAdapter(resolverId, cacheLockingResolver);
            moduleVersionRepository = resolveModeOverride.overrideModuleVersionRepository(moduleVersionRepository);
            ModuleVersionRepository cachingRepository =
                    new CachingModuleVersionRepository(moduleVersionRepository, moduleResolutionCache, moduleDescriptorCache, artifactResolutionCache, artifactFileStore, cachePolicy);
            // Need to contextualise outside of caching, since parsing of module descriptors in the cache requires ivy settings, which is provided via the context atm
            ModuleVersionRepository ivyContextualisedRepository = contextualiser.contextualise(ModuleVersionRepository.class, cachingRepository);
            userResolverChain.add(ivyContextualisedRepository);
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
