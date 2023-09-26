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
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.work.WorkerThreadRegistry;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

/**
 * Factory for creating {@link org.gradle.api.artifacts.Configuration} instances.
 */
@ThreadSafe
public class DefaultConfigurationFactory {

    private final Instantiator instantiator;
    private final ConfigurationResolver resolver;
    private final ListenerManager listenerManager;
    private final DependencyMetaDataProvider metaDataProvider;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final DependencyLockingProvider dependencyLockingProvider;
    private final DomainObjectContext domainObjectContext;
    private final FileCollectionFactory fileCollectionFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final ImmutableAttributesFactory attributesFactory;
    private final ResolveExceptionContextualizer exceptionContextualizer;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final ProjectStateRegistry projectStateRegistry;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final TaskDependencyFactory taskDependencyFactory;

    @Inject
    public DefaultConfigurationFactory(
        Instantiator instantiator,
        ConfigurationResolver resolver,
        ListenerManager listenerManager,
        DependencyMetaDataProvider metaDataProvider,
        ComponentIdentifierFactory componentIdentifierFactory,
        DependencyLockingProvider dependencyLockingProvider,
        DomainObjectContext domainObjectContext,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationExecutor buildOperationExecutor,
        PublishArtifactNotationParserFactory artifactNotationParserFactory,
        ImmutableAttributesFactory attributesFactory,
        ResolveExceptionContextualizer exceptionContextualizer,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        WorkerThreadRegistry workerThreadRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.listenerManager = listenerManager;
        this.metaDataProvider = metaDataProvider;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.domainObjectContext = domainObjectContext;
        this.fileCollectionFactory = fileCollectionFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.artifactNotationParser = artifactNotationParserFactory.create();
        this.capabilityNotationParser = new CapabilityNotationParserFactory(true).create();
        this.attributesFactory = attributesFactory;
        this.exceptionContextualizer = exceptionContextualizer;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.projectStateRegistry = projectStateRegistry;
        this.workerThreadRegistry = workerThreadRegistry;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.taskDependencyFactory = taskDependencyFactory;
    }

    /**
     * Creates a new unlocked configuration instance.
     */
    DefaultUnlockedConfiguration create(
        String name,
        ConfigurationsProvider configurationsProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder,
        ConfigurationRole role
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        DefaultUnlockedConfiguration instance = instantiator.newInstance(
                DefaultUnlockedConfiguration.class,
                domainObjectContext,
                name,
                configurationsProvider,
                resolver,
                dependencyResolutionListeners,
                listenerManager.getBroadcaster(ProjectDependencyObservedListener.class),
                metaDataProvider,
                componentIdentifierFactory,
                dependencyLockingProvider,
                resolutionStrategyFactory,
                fileCollectionFactory,
                buildOperationExecutor,
                instantiator,
                artifactNotationParser,
                capabilityNotationParser,
                attributesFactory,
                rootComponentMetadataBuilder,
                exceptionContextualizer,
                userCodeApplicationContext,
                projectStateRegistry,
                workerThreadRegistry,
                domainObjectCollectionFactory,
                calculatedValueContainerFactory,
                this,
                taskDependencyFactory,
                role
        );
        instance.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return instance;
    }

    /**
     * Creates a new locked resolvable configuration instance.
     */
    DefaultResolvableConfiguration createResolvable(
        String name,
        ConfigurationsProvider configurationsProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        DefaultResolvableConfiguration instance = instantiator.newInstance(
            DefaultResolvableConfiguration.class,
            domainObjectContext,
            name,
            configurationsProvider,
            resolver,
            dependencyResolutionListeners,
            listenerManager.getBroadcaster(ProjectDependencyObservedListener.class),
            metaDataProvider,
            componentIdentifierFactory,
            dependencyLockingProvider,
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationExecutor,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            userCodeApplicationContext,
            projectStateRegistry,
            workerThreadRegistry,
            domainObjectCollectionFactory,
            calculatedValueContainerFactory,
            this,
            taskDependencyFactory
        );
        instance.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return instance;
    }

    /**
     * Creates a new locked consumable configuration instance.
     */
    DefaultConsumableConfiguration createConsumable(
        String name,
        ConfigurationsProvider configurationsProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        DefaultConsumableConfiguration instance = instantiator.newInstance(
            DefaultConsumableConfiguration.class,
            domainObjectContext,
            name,
            configurationsProvider,
            resolver,
            dependencyResolutionListeners,
            listenerManager.getBroadcaster(ProjectDependencyObservedListener.class),
            metaDataProvider,
            componentIdentifierFactory,
            dependencyLockingProvider,
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationExecutor,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            userCodeApplicationContext,
            projectStateRegistry,
            workerThreadRegistry,
            domainObjectCollectionFactory,
            calculatedValueContainerFactory,
            this,
            taskDependencyFactory
        );
        instance.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return instance;
    }

    /**
     * Creates a new locked dependency scope configuration instance.
     */
    DefaultDependencyScopeConfiguration createDependencyScope(
        String name,
        ConfigurationsProvider configurationsProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        DefaultDependencyScopeConfiguration instance = instantiator.newInstance(
            DefaultDependencyScopeConfiguration.class,
            domainObjectContext,
            name,
            configurationsProvider,
            resolver,
            dependencyResolutionListeners,
            listenerManager.getBroadcaster(ProjectDependencyObservedListener.class),
            metaDataProvider,
            componentIdentifierFactory,
            dependencyLockingProvider,
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationExecutor,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            userCodeApplicationContext,
            projectStateRegistry,
            workerThreadRegistry,
            domainObjectCollectionFactory,
            calculatedValueContainerFactory,
            this,
            taskDependencyFactory
        );
        instance.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return instance;
    }
}
