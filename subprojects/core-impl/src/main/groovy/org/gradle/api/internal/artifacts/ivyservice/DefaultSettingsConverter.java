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

import org.apache.commons.lang.StringUtils;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.plugins.repository.TransferListener;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.internal.Factory;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsConverter implements SettingsConverter {
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Factory<IvySettings> settingsFactory;
    private final TransferListener transferListener = new ProgressLoggingTransferListener();
    private IvySettings publishSettings;
    private IvySettings resolveSettings;
    private ChainResolver userResolverChain;
    private DependencyResolver outerChain;

    public DefaultSettingsConverter(ProgressLoggerFactory progressLoggerFactory, Factory<IvySettings> settingsFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.settingsFactory = settingsFactory;
    }

    private static String getLengthText(TransferEvent evt) {
        return getLengthText(evt.isTotalLengthSet() ? evt.getTotalLength() : null);
    }

    private static String getLengthText(Long bytes) {
        if (bytes == null) {
            return "unknown size";
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1048576) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format("%.2f MB", bytes / 1048576.0);
        }
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

    public IvySettings convertForResolve(List<DependencyResolver> dependencyResolvers, Map<String, ModuleDescriptor> clientModuleRegistry) {
        if (resolveSettings == null) {
            resolveSettings = settingsFactory.create();
            userResolverChain = createUserResolverChain();
            DependencyResolver clientModuleResolver = createClientModuleResolver(clientModuleRegistry, userResolverChain);
            outerChain = createOuterChain(WrapUtil.toLinkedSet(clientModuleResolver, userResolverChain));
            initializeResolvers(resolveSettings, WrapUtil.toList(userResolverChain, clientModuleResolver, outerChain));
        }
        replaceResolvers(dependencyResolvers, userResolverChain);
        initializeResolvers(resolveSettings, dependencyResolvers);
        resolveSettings.setDefaultResolver(outerChain.getName());
        return resolveSettings;
    }

    private DependencyResolver createOuterChain(Collection<DependencyResolver> resolvers) {
        ChainResolver clientModuleChain = new ChainResolver();
        clientModuleChain.setName(CLIENT_MODULE_CHAIN_NAME);
        clientModuleChain.setReturnFirst(true);
        clientModuleChain.setRepositoryCacheManager(new NoOpRepositoryCacheManager(clientModuleChain.getName()));
        for (DependencyResolver resolver : resolvers) {
            clientModuleChain.add(resolver);
        }
        return clientModuleChain;
    }

    private DependencyResolver createClientModuleResolver(Map<String, ModuleDescriptor> clientModuleRegistry, DependencyResolver userResolverChain) {
        return new ClientModuleResolver(CLIENT_MODULE_NAME, clientModuleRegistry, userResolverChain);
    }

    private ChainResolver createUserResolverChain() {
        ChainResolver chainResolver = new ChainResolver();
        chainResolver.setName(CHAIN_RESOLVER_NAME);
        chainResolver.setReturnFirst(true);
        chainResolver.setRepositoryCacheManager(new NoOpRepositoryCacheManager(chainResolver.getName()));
        return chainResolver;
    }

    private void replaceResolvers(List<DependencyResolver> classpathResolvers, ChainResolver chainResolver) {
        Collection<DependencyResolver> oldResolvers = new ArrayList<DependencyResolver>(chainResolver.getResolvers());
        for (DependencyResolver resolver : oldResolvers) {
            resolveSettings.getResolvers().remove(resolver);
            chainResolver.getResolvers().remove(resolver);
        }
        for (DependencyResolver classpathResolver : classpathResolvers) {
            chainResolver.add(classpathResolver);
        }
    }

    private void initializeResolvers(IvySettings ivySettings, List<DependencyResolver> allResolvers) {
        for (DependencyResolver dependencyResolver : allResolvers) {
            ivySettings.addResolver(dependencyResolver);
            RepositoryCacheManager cacheManager = dependencyResolver.getRepositoryCacheManager();
            // Ensure that each resolver is sharing the same cache instance (ignoring caches which don't actually cache anything)
            if (!(cacheManager instanceof NoOpRepositoryCacheManager) && !(cacheManager instanceof LocalFileRepositoryCacheManager)) {
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

    private class ProgressLoggingTransferListener implements TransferListener {
        private ProgressLogger logger;
        private long total;

        public void transferProgress(TransferEvent evt) {
            if (evt.getResource().isLocal()) {
                return;
            }
            if (evt.getEventType() == TransferEvent.TRANSFER_STARTED) {
                total = 0;
                logger = progressLoggerFactory.newOperation(DefaultSettingsConverter.class);
                String description = String.format("%s %s", StringUtils.capitalize(getRequestType(evt)), evt.getResource().getName());
                logger.setDescription(description);
                logger.setLoggingHeader(description);
                logger.started();
            }
            if (evt.getEventType() == TransferEvent.TRANSFER_PROGRESS) {
                total += evt.getLength();
                logger.progress(String.format("%s/%s %sed", getLengthText(total), getLengthText(evt), getRequestType(evt)));
            }
            if (evt.getEventType() == TransferEvent.TRANSFER_COMPLETED) {
                logger.completed();
            }
        }

        private String getRequestType(TransferEvent evt) {
            if (evt.getRequestType() == TransferEvent.REQUEST_PUT) {
                return "upload";
            } else {
                return "download";
            }
        }
    }
}
