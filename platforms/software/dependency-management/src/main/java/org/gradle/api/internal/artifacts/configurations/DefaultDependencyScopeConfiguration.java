/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.work.WorkerThreadRegistry;

/**
 * A concrete dependency scope {@link DefaultConfiguration} that cannot change roles.
 */
public class DefaultDependencyScopeConfiguration extends DefaultConfiguration implements DependencyScopeConfiguration {

    public DefaultDependencyScopeConfiguration(
        DomainObjectContext domainObjectContext,
        String name,
        ConfigurationsProvider configurationsProvider,
        ConfigurationResolver resolver,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners,
        DependencyMetaDataProvider metaDataProvider,
        ComponentIdentifierFactory componentIdentifierFactory,
        DependencyLockingProvider dependencyLockingProvider,
        Factory<ResolutionStrategyInternal> resolutionStrategyFactory,
        FileCollectionFactory fileCollectionFactory,
        BuildOperationRunner buildOperationRunner,
        Instantiator instantiator,
        NotationParser<Object, ConfigurablePublishArtifact> artifactNotationParser,
        NotationParser<Object, Capability> capabilityNotationParser,
        ImmutableAttributesFactory attributesFactory,
        RootComponentMetadataBuilder rootComponentMetadataBuilder,
        ResolveExceptionMapper exceptionMapper,
        AttributeDesugaring attributeDesugaring,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        WorkerThreadRegistry workerThreadRegistry,
        DomainObjectCollectionFactory domainObjectCollectionFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        DefaultConfigurationFactory defaultConfigurationFactory,
        TaskDependencyFactory taskDependencyFactory
    ) {
        super(
            domainObjectContext,
            name,
            configurationsProvider,
            resolver,
            dependencyResolutionListeners,
            metaDataProvider,
            componentIdentifierFactory,
            dependencyLockingProvider,
            resolutionStrategyFactory,
            fileCollectionFactory,
            buildOperationRunner,
            instantiator,
            artifactNotationParser,
            capabilityNotationParser,
            attributesFactory,
            rootComponentMetadataBuilder,
            exceptionMapper,
            attributeDesugaring,
            userCodeApplicationContext,
            projectStateRegistry,
            workerThreadRegistry,
            domainObjectCollectionFactory,
            calculatedValueContainerFactory,
            defaultConfigurationFactory,
            taskDependencyFactory,
            ConfigurationRoles.DEPENDENCY_SCOPE,
            true
        );

        setVisible(false);
    }

}
