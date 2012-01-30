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
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.SettingsConverter;
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
    private final CacheLockingManager cacheLockingManager;
    private final StartParameterResolutionOverride startParameterResolutionOverride;

    public ResolveIvyFactory(IvyFactory ivyFactory, ResolverProvider resolverProvider, SettingsConverter settingsConverter,
                             ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache,
                             ArtifactResolutionCache artifactResolutionCache,
                             CacheLockingManager cacheLockingManager, StartParameterResolutionOverride startParameterResolutionOverride) {
        this.ivyFactory = ivyFactory;
        this.resolverProvider = resolverProvider;
        this.settingsConverter = settingsConverter;
        this.moduleResolutionCache = moduleResolutionCache;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.artifactResolutionCache = artifactResolutionCache;
        this.cacheLockingManager = cacheLockingManager;
        this.startParameterResolutionOverride = startParameterResolutionOverride;
    }

    public IvyAdapter create(ConfigurationInternal configuration) {
        UserResolverChain userResolverChain = new UserResolverChain();
        ResolutionRules resolutionRules = configuration.getResolutionStrategy().getResolutionRules();
        startParameterResolutionOverride.addResolutionRules(resolutionRules);

        LoopbackDependencyResolver loopbackDependencyResolver = new LoopbackDependencyResolver(SettingsConverter.LOOPBACK_RESOLVER_NAME, userResolverChain, cacheLockingManager);
        List<DependencyResolver> rawResolvers = resolverProvider.getResolvers();

        // Unfortunately, WharfResolverMetadata requires the resolver to have settings to calculate an id.
        // We then need to set the ivySettings on the delegating resolver as well
        IvySettings ivySettings = settingsConverter.convertForResolve(loopbackDependencyResolver, rawResolvers);
        Ivy ivy = ivyFactory.createIvy(ivySettings);
        ResolveData resolveData = createResolveData(ivy, configuration.getName());

        IvyContextualiser contextualiser = new IvyContextualiser(ivy, resolveData);
        for (DependencyResolver rawResolver : rawResolvers) {
            rawResolver.setSettings(ivySettings);
            String resolverId = new WharfResolverMetadata(rawResolver).getId();

            ModuleVersionRepository moduleVersionRepository = new DependencyResolverAdapter(resolverId, rawResolver);
            moduleVersionRepository = new CacheLockingModuleVersionRepository(moduleVersionRepository, cacheLockingManager);
            moduleVersionRepository = startParameterResolutionOverride.overrideModuleVersionRepository(moduleVersionRepository);
            ModuleVersionRepository cachingRepository =
                    new CachingModuleVersionRepository(moduleVersionRepository, moduleResolutionCache, moduleDescriptorCache, artifactResolutionCache,
                                                       configuration.getResolutionStrategy().getCachePolicy());
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
