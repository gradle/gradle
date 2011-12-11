/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.TransferListener;
import org.apache.ivy.plugins.resolver.*;
import org.apache.ivy.util.Message;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactFileStore;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;
import org.gradle.api.internal.artifacts.repositories.ProgressLoggingTransferListener;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.WrapUtil;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsConverter implements SettingsConverter {
    static final String LOOPBACK_RESOLVER_NAME = "main";
    private final Factory<IvySettings> settingsFactory;
    private final Map<String, DependencyResolver> resolversById = new HashMap<String, DependencyResolver>();
    private final TransferListener transferListener;
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;
    private final ArtifactFileStore artifactFileStore;
    private final CacheLockingManager cacheLockingManager;
    private IvySettings publishSettings;
    private IvySettings resolveSettings;
    private UserResolverChain userResolverChain;

    public DefaultSettingsConverter(ProgressLoggerFactory progressLoggerFactory, Factory<IvySettings> settingsFactory,
                                    ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache, ArtifactFileStore artifactFileStore,
                                    CacheLockingManager cacheLockingManager) {
        this.settingsFactory = settingsFactory;
        this.moduleResolutionCache = moduleResolutionCache;
        this.moduleDescriptorCache = moduleDescriptorCache;
        this.artifactFileStore = artifactFileStore;
        this.cacheLockingManager = cacheLockingManager;
        Message.setDefaultLogger(new IvyLoggingAdaper());
        transferListener = new ProgressLoggingTransferListener(progressLoggerFactory, DefaultSettingsConverter.class);
    }

    public IvySettings convertForPublish(List<DependencyResolver> publishResolvers) {
        if (publishSettings == null) {
            publishSettings = settingsFactory.create();
        } else {
            publishSettings.getResolvers().clear();
        }
        initializeResolvers(publishSettings, publishResolvers);
        return publishSettings;
    }

    public IvySettings convertForResolve(List<DependencyResolver> dependencyResolvers, ResolutionStrategyInternal resolutionStrategy) {
        if (resolveSettings == null) {
            resolveSettings = settingsFactory.create();
            userResolverChain = createUserResolverChain();
            resolveSettings.addResolver(userResolverChain);
            LoopbackDependencyResolver loopbackDependencyResolver = new LoopbackDependencyResolver(LOOPBACK_RESOLVER_NAME, userResolverChain, cacheLockingManager);
            resolveSettings.addResolver(loopbackDependencyResolver);
            resolveSettings.setDefaultResolver(LOOPBACK_RESOLVER_NAME);
        }

        moduleDescriptorCache.setSettings(resolveSettings);
        userResolverChain.setCachePolicy(resolutionStrategy.getCachePolicy());
        replaceResolvers(dependencyResolvers, userResolverChain);
        return resolveSettings;
    }

    private UserResolverChain createUserResolverChain() {
        UserResolverChain chainResolver = new UserResolverChain(moduleResolutionCache, moduleDescriptorCache, artifactFileStore);
        chainResolver.setName(USER_RESOLVER_CHAIN_NAME);
        chainResolver.setReturnFirst(true);
        chainResolver.setRepositoryCacheManager(new NoOpRepositoryCacheManager(chainResolver.getName()));
        return chainResolver;
    }

    private void replaceResolvers(List<DependencyResolver> resolvers, ChainResolver chainResolver) {
        Collection<DependencyResolver> oldResolvers = new ArrayList<DependencyResolver>(chainResolver.getResolvers());
        for (DependencyResolver resolver : oldResolvers) {
            resolveSettings.getResolvers().remove(resolver);
            chainResolver.getResolvers().remove(resolver);
        }
        for (DependencyResolver resolver : resolvers) {
            resolver.setSettings(resolveSettings);
            String resolverId = new WharfResolverMetadata(resolver).getId();
            DependencyResolver sharedResolver = resolversById.get(resolverId);
            if (sharedResolver == null) {
                initializeResolvers(resolveSettings, WrapUtil.toList(resolver));
                sharedResolver = new DependencyResolverAdapter(resolverId, resolver, cacheLockingManager);
                resolversById.put(resolverId, sharedResolver);
            }
            resolveSettings.addResolver(sharedResolver);
            assert resolveSettings.getResolver(resolver.getName()) == sharedResolver;
            chainResolver.add(sharedResolver);
        }
    }

    private void initializeResolvers(IvySettings ivySettings, List<DependencyResolver> allResolvers) {
        for (DependencyResolver dependencyResolver : allResolvers) {
            dependencyResolver.setSettings(ivySettings);
            RepositoryCacheManager existingCacheManager = dependencyResolver.getRepositoryCacheManager();
            // Ensure that each resolver is sharing the same cache instance (ignoring caches which don't actually cache anything)
            if (!(existingCacheManager instanceof NoOpRepositoryCacheManager) && !(existingCacheManager instanceof LocalFileRepositoryCacheManager)) {
                ((AbstractResolver) dependencyResolver).setRepositoryCacheManager(ivySettings.getDefaultRepositoryCacheManager());
            }
            attachListener(dependencyResolver);
        }
    }

    private void attachListener(DependencyResolver dependencyResolver) {
        if (dependencyResolver instanceof RepositoryResolver) {
            Repository repository = ((RepositoryResolver) dependencyResolver).getRepository();
            if (!repository.hasTransferListener(transferListener)) {
                repository.addTransferListener(transferListener);
            }
        }
        if (dependencyResolver instanceof DualResolver) {
            DualResolver dualResolver = (DualResolver) dependencyResolver;
            attachListener(dualResolver.getIvyResolver());
            attachListener(dualResolver.getArtifactResolver());
        }
        if (dependencyResolver instanceof ChainResolver) {
            ChainResolver chainResolver = (ChainResolver) dependencyResolver;
            List<DependencyResolver> resolvers = chainResolver.getResolvers();
            for (DependencyResolver resolver : resolvers) {
                attachListener(resolver);
            }
        }
    }

}
