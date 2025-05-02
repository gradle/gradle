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
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.model.CalculatedValueFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
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

    private final Instantiator instantiator;
    private final ConfigurationResolver resolver;
    private final ListenerManager listenerManager;
    private final DomainObjectContext domainObjectContext;
    private final FileCollectionFactory fileCollectionFactory;
    private final BuildOperationRunner buildOperationRunner;
    private final NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser;
    private final NotationParser<Object, Capability> capabilityNotationParser;
    private final AttributesFactory attributesFactory;
    private final ResolveExceptionMapper exceptionContextualizer;
    private final AttributeDesugaring attributeDesugaring;
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final CollectionCallbackActionDecorator collectionCallbackActionDecorator;
    private final ProjectStateRegistry projectStateRegistry;
    private final DomainObjectCollectionFactory domainObjectCollectionFactory;
    private final CalculatedValueFactory calculatedValueFactory;
    private final TaskDependencyFactory taskDependencyFactory;
    private final InternalProblems problemsService;
    private final DocumentationRegistry documentationRegistry;

    @Inject
    public DefaultConfigurationFactory(
        Instantiator instantiator,
        ConfigurationResolver resolver,
        ListenerManager listenerManager,
        DomainObjectContext domainObjectContext,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationRunner buildOperationRunner,
        PublishArtifactNotationParserFactory artifactNotationParserFactory,
        AttributesFactory attributesFactory,
        ResolveExceptionMapper exceptionMapper,
        AttributeDesugaring attributeDesugaring,
        UserCodeApplicationContext userCodeApplicationContext,
        CollectionCallbackActionDecorator collectionCallbackActionDecorator,
        ProjectStateRegistry projectStateRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueFactory calculatedValueFactory,
        TaskDependencyFactory taskDependencyFactory,
        InternalProblems problemsService,
        DocumentationRegistry documentationRegistry
    ) {
        this.instantiator = instantiator;
        this.resolver = resolver;
        this.listenerManager = listenerManager;
        this.domainObjectContext = domainObjectContext;
        this.fileCollectionFactory = fileCollectionFactory;
        this.buildOperationRunner = buildOperationRunner;
        this.artifactNotationParser = artifactNotationParserFactory.create();
        this.capabilityNotationParser = new CapabilityNotationParserFactory(true).create();
        this.attributesFactory = attributesFactory;
        this.exceptionContextualizer = exceptionMapper;
        this.attributeDesugaring = attributeDesugaring;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
        this.projectStateRegistry = projectStateRegistry;
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.calculatedValueFactory = calculatedValueFactory;
        this.taskDependencyFactory = taskDependencyFactory;
        this.problemsService = problemsService;
        this.documentationRegistry = documentationRegistry;
    }

    /**
     * Creates a new unlocked configuration instance.
     */
    DefaultLegacyConfiguration create(
        String name,
        ConfigurationsProvider configurationsProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder,
        ConfigurationRole role
    ) {
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners =
            listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);
        DefaultLegacyConfiguration instance = instantiator.newInstance(
            DefaultLegacyConfiguration.class,
            domainObjectContext,
            name,
            configurationsProvider,
            resolver,
            dependencyResolutionListeners,
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationRunner,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            collectionCallbackActionDecorator,
            projectStateRegistry,
            domainObjectCollectionFactory,
            calculatedValueFactory,
            this,
            taskDependencyFactory,
            role,
            problemsService,
            documentationRegistry
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
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationRunner,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            collectionCallbackActionDecorator,
            projectStateRegistry,
            domainObjectCollectionFactory,
            calculatedValueFactory,
            this,
            taskDependencyFactory,
            problemsService,
            documentationRegistry
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
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationRunner,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            collectionCallbackActionDecorator,
            projectStateRegistry,
            domainObjectCollectionFactory,
            calculatedValueFactory,
            this,
            taskDependencyFactory,
            problemsService,
            documentationRegistry
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
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationRunner,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionContextualizer,
            attributeDesugaring,
            userCodeApplicationContext,
            collectionCallbackActionDecorator,
            projectStateRegistry,
            domainObjectCollectionFactory,
            calculatedValueFactory,
            this,
            taskDependencyFactory,
            problemsService,
            documentationRegistry
        );
        instance.addMutationValidator(rootComponentMetadataBuilder.getValidator());
        return instance;
    }

    public InternalProblems getProblems() {
        return problemsService;
    }
}
