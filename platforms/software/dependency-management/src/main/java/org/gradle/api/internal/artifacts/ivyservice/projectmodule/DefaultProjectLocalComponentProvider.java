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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;

/**
 * Provides the metadata for a component consumed from the same build that produces it.
 *
 * <p>Currently, the metadata for a component is different based on whether it is consumed from the producing build or from another build. This difference should go away.
 */
public class DefaultProjectLocalComponentProvider implements LocalComponentProvider {
    private final LocalConfigurationMetadataBuilder metadataBuilder;
    private final LocalComponentGraphResolveStateFactory resolveStateFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public DefaultProjectLocalComponentProvider(
        LocalConfigurationMetadataBuilder metadataBuilder,
        LocalComponentGraphResolveStateFactory resolveStateFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.metadataBuilder = metadataBuilder;
        this.resolveStateFactory = resolveStateFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectState projectState) {
        projectState.ensureConfigured();
        LocalComponentMetadata metadata = projectState.fromMutableState(p -> getLocalComponentMetadata(projectState, p));
        return resolveStateFactory.stateFor(metadata);
    }

    private LocalComponentMetadata getLocalComponentMetadata(ProjectState projectState, ProjectInternal project) {
        Module module = project.getDependencyMetaDataProvider().getModule();
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectComponentIdentifier componentIdentifier = projectState.getComponentIdentifier();
        AttributesSchemaInternal schema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();

        DefaultLocalComponentMetadata.ConfigurationsProviderMetadataFactory configurationMetadataFactory =
            new DefaultLocalComponentMetadata.ConfigurationsProviderMetadataFactory(
                (DefaultConfigurationContainer) project.getConfigurations(), metadataBuilder, projectState, calculatedValueContainerFactory);

        project.getConfigurations().forEach(conf -> ((ConfigurationInternal) conf).preventFromFurtherMutation());

        return new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), schema, configurationMetadataFactory, null);
    }
}
