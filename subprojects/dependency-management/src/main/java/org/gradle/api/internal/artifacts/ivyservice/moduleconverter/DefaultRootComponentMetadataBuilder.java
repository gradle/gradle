/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;

public class DefaultRootComponentMetadataBuilder implements RootComponentMetadataBuilder {
    private final DependencyMetaDataProvider metaDataProvider;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ProjectFinder projectFinder;
    private final ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder;
    private final ConfigurationsProvider configurationsProvider;
    private final MetadataHolder holder;

    public DefaultRootComponentMetadataBuilder(DependencyMetaDataProvider metaDataProvider,
                                               ComponentIdentifierFactory componentIdentifierFactory,
                                               ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                               ProjectFinder projectFinder,
                                               ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder,
                                               ConfigurationsProvider configurationsProvider) {
        this.metaDataProvider = metaDataProvider;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.projectFinder = projectFinder;
        this.configurationComponentMetaDataBuilder = configurationComponentMetaDataBuilder;
        this.configurationsProvider = configurationsProvider;
        this.holder = new MetadataHolder();
    }

    @Override
    public ComponentResolveMetadata toRootComponentMetaData() {
        Module module = metaDataProvider.getModule();
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module);
        DefaultLocalComponentMetadata metaData = holder.tryCached(componentIdentifier);
        if (metaData != null) {
            return metaData;
        }
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectInternal project = projectFinder.findProject(module.getProjectPath());
        AttributesSchemaInternal schema = project == null ? null : (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
        metaData = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), schema);
        configurationComponentMetaDataBuilder.addConfigurations(metaData, configurationsProvider.getAll());
        holder.cachedValue = metaData;
        return metaData;
    }

    public RootComponentMetadataBuilder withConfigurationsProvider(ConfigurationsProvider alternateProvider) {
        return new DefaultRootComponentMetadataBuilder(
            metaDataProvider, componentIdentifierFactory, moduleIdentifierFactory, projectFinder, configurationComponentMetaDataBuilder, alternateProvider
        );
    }

    public MutationValidator getValidator() {
        return holder;
    }

    private static class MetadataHolder implements MutationValidator {
        private DefaultLocalComponentMetadata cachedValue;

        @Override
        public void validateMutation(MutationType type) {
            if (type == MutationType.DEPENDENCIES || type == MutationType.ARTIFACTS || type == MutationType.DEPENDENCY_ATTRIBUTES) {
                cachedValue = null;
            }
        }

        DefaultLocalComponentMetadata tryCached(ComponentIdentifier id) {
            if (cachedValue != null) {
                if (cachedValue.getComponentId().equals(id)) {
                    return cachedValue;
                }
                cachedValue = null;
            }
            return null;
        }
    }
}
