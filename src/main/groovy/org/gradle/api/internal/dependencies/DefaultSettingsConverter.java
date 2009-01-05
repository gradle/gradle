/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.plugins.repository.TransferListener;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.DependencyManager;
import org.gradle.api.Transformer;
import org.gradle.api.dependencies.SettingsConverter;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputLogging;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsConverter implements SettingsConverter {
    private static Logger logger = LoggerFactory.getLogger(DefaultSettingsConverter.class);

    private IvySettings ivySettings;

    private ChainingTransformer<IvySettings> transformer = new ChainingTransformer<IvySettings>(IvySettings.class);

    public void addIvyTransformer(Transformer<IvySettings> transformer) {
        this.transformer.add(transformer);
    }

    public void addIvyTransformer(Closure tranformer) {
        this.transformer.add(tranformer);
    }

    public IvySettings convert(List<DependencyResolver> classpathResolvers, List<DependencyResolver> otherResolvers, File gradleUserHome, RepositoryResolver buildResolver,
                               Map clientModuleRegistry) {
        if (ivySettings != null) {
            return ivySettings;
        }
        IvySettings ivySettings = new IvySettings();
        ivySettings.setDefaultCache(new File(gradleUserHome, DependencyManager.DEFAULT_CACHE_DIR_NAME));
        ivySettings.setDefaultCacheIvyPattern(DependencyManager.DEFAULT_CACHE_IVY_PATTERN);
        ivySettings.setDefaultCacheArtifactPattern(DependencyManager.DEFAULT_CACHE_ARTIFACT_PATTERN);
        ClientModuleResolver clientModuleResolver = new ClientModuleResolver();
        clientModuleResolver.setModuleRegistry(clientModuleRegistry);
        clientModuleResolver.setName(CLIENT_MODULE_NAME);
        ChainResolver chainResolver = new ChainResolver();
        chainResolver.setName(CHAIN_RESOLVER_NAME);
        chainResolver.add(buildResolver);
        // todo Figure out why Ivy thinks this is necessary. The IBiblio resolver has already this pattern which should be good enough. By doing this we let Maven semantics seep into our whole system.
        chainResolver.setChangingPattern(".*-SNAPSHOT");
        chainResolver.setChangingMatcher(PatternMatcher.REGEXP);

        for (Object classpathResolver : classpathResolvers) {
            chainResolver.add((DependencyResolver) classpathResolver);
        }
        chainResolver.setReturnFirst(true);
        clientModuleResolver.setMainResolver(chainResolver);
        ChainResolver clientModuleChain = new ChainResolver();
        clientModuleChain.setName(CLIENT_MODULE_CHAIN_NAME);
        clientModuleChain.setReturnFirst(true);
        clientModuleChain.add(clientModuleResolver);
        clientModuleChain.add(chainResolver);
        List<DependencyResolver> allResolvers = new ArrayList(otherResolvers);
        allResolvers.addAll(classpathResolvers);
        allResolvers.addAll(WrapUtil.toList(buildResolver, clientModuleChain, clientModuleResolver, chainResolver));
        addTransferListener(ivySettings, allResolvers);
        ivySettings.setDefaultResolver(CLIENT_MODULE_CHAIN_NAME);
        ivySettings.setVariable("ivy.log.modules.in.use", "false");

        return ivySettings;
    }

    private void addTransferListener(IvySettings ivySettings, List<DependencyResolver> allResolvers) {
        TransferListener transferListener = new TransferListener() {
            public void transferProgress(TransferEvent evt) {
                if (evt.getResource().isLocal()) {
                    return;
                }
                if (evt.getEventType() == TransferEvent.TRANSFER_STARTED) {
                    logger.info(Logging.LIFECYCLE_ALLWAYS, String.format("downloading (%s) %s", getLengthText(evt), evt.getResource().getName()));
                }
                if (evt.getEventType() == TransferEvent.TRANSFER_PROGRESS) {
                    StandardOutputLogging.printToDefaultOut(".");
                }
                if (evt.getEventType() == TransferEvent.TRANSFER_COMPLETED || evt.getEventType() == TransferEvent.TRANSFER_ERROR) {
                    StandardOutputLogging.printToDefaultOut(String.format("%n"));
                }
            }
        };
        for (DependencyResolver dependencyResolver : allResolvers) {
            ivySettings.addResolver(dependencyResolver);
            ((DefaultRepositoryCacheManager) dependencyResolver.getRepositoryCacheManager()).setSettings(ivySettings);
            if (dependencyResolver instanceof RepositoryResolver) {
                ((RepositoryResolver) dependencyResolver).getRepository().addTransferListener(transferListener);
            }
        }
    }

    private String getLengthText(TransferEvent evt) {
        if (!evt.isTotalLengthSet()) {
            return "unknown size";
        }
        long lengthInByte = evt.getTotalLength();
        if (lengthInByte < 1000) {
            return lengthInByte + " B";
        } else if (lengthInByte < 1000000) {
            return (lengthInByte / 1000) + " KB";
        } else {
            return String.format("%.2f MB", lengthInByte / 1000000.0);
        }
    }

    public IvySettings getIvySettings() {
        return ivySettings;
    }

    public void setIvySettings(IvySettings ivySettings) {
        this.ivySettings = ivySettings;
    }
}
