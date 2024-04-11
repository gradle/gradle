/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import javax.inject.Inject;

/**
 * Creates {@link RootComponentStateBuilder} instances.
 */
public class RootComponentStateBuilderFactory {
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;
    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    @Inject
    public RootComponentStateBuilderFactory(
        ComponentIdentifierFactory componentIdentifierFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        LocalConfigurationMetadataBuilder configurationMetadataBuilder,
        ProjectStateRegistry projectStateRegistry,
        LocalComponentGraphResolveStateFactory localResolveStateFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.configurationMetadataBuilder = configurationMetadataBuilder;
        this.projectStateRegistry = projectStateRegistry;
        this.localResolveStateFactory = localResolveStateFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    public RootComponentStateBuilder create(ConfigurationsProvider configurationsProvider, DependencyMetaDataProvider metadataProvider) {
        return new DefaultRootComponentStateBuilder(
            metadataProvider,
            componentIdentifierFactory,
            moduleIdentifierFactory,
            configurationMetadataBuilder,
            configurationsProvider,
            projectStateRegistry,
            localResolveStateFactory,
            calculatedValueContainerFactory
        );
    }
}
