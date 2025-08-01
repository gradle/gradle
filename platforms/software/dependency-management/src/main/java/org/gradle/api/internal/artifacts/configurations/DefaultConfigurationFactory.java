/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.ConfigurationServicesBundle;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

/**
 * Factory for creating {@link org.gradle.api.artifacts.Configuration} instances.
 */
@ServiceScope(Scope.Project.class)
@ThreadSafe
public class DefaultConfigurationFactory {
    private final ConfigurationServicesBundle configurationServices;
    private final ListenerManager listenerManager;
    private final DomainObjectContext domainObjectContext;
    private final BuildOperationRunner buildOperationRunner;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final ResolveExceptionMapper exceptionContextualizer;
    private final AttributeDesugaring attributeDesugaring;
    private final UserCodeApplicationContext userCodeApplicationContext;

    @Inject
    public DefaultConfigurationFactory(
        ConfigurationServicesBundle configurationServices,
        ListenerManager listenerManager,
        DomainObjectContext domainObjectContext,
        BuildOperationRunner buildOperationRunner,
        PublishArtifactNotationParserFactory artifactNotationParserFactory,
        ResolveExceptionMapper exceptionMapper,
        AttributeDesugaring attributeDesugaring,
        UserCodeApplicationContext userCodeApplicationContext
    ) {
        this.configurationServices = configurationServices;
        this.listenerManager = listenerManager;
        this.domainObjectContext = domainObjectContext;
        this.buildOperationRunner = buildOperationRunner;
        this.artifactNotationParser = artifactNotationParserFactory.create();
        this.capabilityNotationParser = new CapabilityNotationParserFactory(true).create();
        this.exceptionContextualizer = exceptionMapper;
        this.attributeDesugaring = attributeDesugaring;
        this.userCodeApplicationContext = userCodeApplicationContext;
    }

    /**
     * Creates a new unlocked configuration instance.
     */
    DefaultLegacyConfiguration create(
        String name,
        boolean isDetached,
        ConfigurationResolver resolver,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        ConfigurationRole role
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        return configurationServices.getObjectFactory().newInstance(
            DefaultLegacyConfiguration.class,
            configurationServices,
            domainObjectContext,
            name,
            isDetached,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            buildOperationRunner,
            artifactNotationParser,
            capabilityNotationParser,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            this,
            role
        );
    }

    /**
     * Creates a new locked resolvable configuration instance.
     */
    DefaultResolvableConfiguration createResolvable(
        String name,
        ConfigurationResolver resolver,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        return configurationServices.getObjectFactory().newInstance(
            DefaultResolvableConfiguration.class,
            configurationServices,
            domainObjectContext,
            name,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            buildOperationRunner,
            artifactNotationParser,
            capabilityNotationParser,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            this
        );
    }

    /**
     * Creates a new locked consumable configuration instance.
     */
    DefaultConsumableConfiguration createConsumable(
        String name,
        ConfigurationResolver resolver,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        return configurationServices.getObjectFactory().newInstance(
            DefaultConsumableConfiguration.class,
            configurationServices,
            domainObjectContext,
            name,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            buildOperationRunner,
            artifactNotationParser,
            capabilityNotationParser,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            this
        );
    }

    /**
     * Creates a new locked dependency scope configuration instance.
     */
    DefaultDependencyScopeConfiguration createDependencyScope(
        String name,
        ConfigurationResolver resolver,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        return configurationServices.getObjectFactory().newInstance(
            DefaultDependencyScopeConfiguration.class,
            configurationServices,
            domainObjectContext,
            name,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            buildOperationRunner,
            artifactNotationParser,
            capabilityNotationParser,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            this
        );
    }
}
