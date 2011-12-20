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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.NoOpRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.ProgressLoggingTransferListener;
import org.gradle.logging.ProgressLoggerFactory;

import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsConverter implements SettingsConverter {
    private final Factory<IvySettings> settingsFactory;
    private final TransferListener transferListener;
    private IvySettings publishSettings;
    private IvySettings resolveSettings;

    public DefaultSettingsConverter(ProgressLoggerFactory progressLoggerFactory, Factory<IvySettings> settingsFactory) {
        this.settingsFactory = settingsFactory;
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

    public IvySettings convertForResolve(DependencyResolver defaultResolver, List<DependencyResolver> resolvers) {
        if (resolveSettings == null) {
            resolveSettings = settingsFactory.create();
        } else {
            resolveSettings.getResolvers().clear();
        }

        resolveSettings.addResolver(defaultResolver);
        resolveSettings.setDefaultResolver(defaultResolver.getName());
        
        for (DependencyResolver resolver : resolvers) {
            resolveSettings.addResolver(resolver);
        }
        initializeResolvers(resolveSettings, resolvers);
        return resolveSettings;
    }

    public IvySettings getForResolve() {
        if (resolveSettings == null) {
            resolveSettings = settingsFactory.create();
        }
        return resolveSettings;
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
