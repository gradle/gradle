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
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.RootLocalComponentMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;

public class DefaultRootComponentMetadataBuilder implements RootComponentMetadataBuilder {
    private final DependencyMetaDataProvider metadataProvider;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalComponentMetadataBuilder localComponentMetadataBuilder;
    private final ConfigurationsProvider configurationsProvider;
    private final MetadataHolder holder;
    private final ProjectStateRegistry projectStateRegistry;
    private final DependencyLockingProvider dependencyLockingProvider;

    public DefaultRootComponentMetadataBuilder(DependencyMetaDataProvider metadataProvider,
                                               ComponentIdentifierFactory componentIdentifierFactory,
                                               ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                               LocalComponentMetadataBuilder localComponentMetadataBuilder,
                                               ConfigurationsProvider configurationsProvider,
                                               ProjectStateRegistry projectStateRegistry,
                                               DependencyLockingProvider dependencyLockingProvider) {
        this.metadataProvider = metadataProvider;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.localComponentMetadataBuilder = localComponentMetadataBuilder;
        this.configurationsProvider = configurationsProvider;
        this.projectStateRegistry = projectStateRegistry;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.holder = new MetadataHolder();
    }

    @Override
    public ComponentResolveMetadata toRootComponentMetaData() {
        Module module = metadataProvider.getModule();
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module);
        DefaultLocalComponentMetadata metadata = holder.tryCached(componentIdentifier);
        if (metadata == null) {
            metadata = buildRootComponentMetadata(module, componentIdentifier);
            holder.cachedValue = metadata;
        }
        return metadata;
    }

    private DefaultLocalComponentMetadata buildRootComponentMetadata(final Module module, final ComponentIdentifier componentIdentifier) {
        final ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectComponentIdentifier projectId = module.getProjectId();
        if (projectId != null) {
            ProjectState projectState = projectStateRegistry.stateFor(projectId);
            if (!projectState.hasMutableState()) {
                throw new IllegalStateException("Thread should hold project lock for " + projectId);
            }
            return projectState.fromMutableState(project -> {
                AttributesSchemaInternal schema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
                return getRootComponentMetadata(module, componentIdentifier, moduleVersionIdentifier, schema, dependencyLockingProvider);
            });
        } else {
            return getRootComponentMetadata(module, componentIdentifier, moduleVersionIdentifier, null, dependencyLockingProvider);
        }
    }

    private DefaultLocalComponentMetadata getRootComponentMetadata(Module module, ComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, AttributesSchemaInternal schema, DependencyLockingProvider dependencyLockingHandler) {
        DefaultLocalComponentMetadata metadata = new RootLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), schema, dependencyLockingHandler);
        for (ConfigurationInternal configuration : configurationsProvider.getAll()) {
            addConfiguration(metadata, configuration);
        }
        return metadata;
    }

    private void addConfiguration(DefaultLocalComponentMetadata metadata, ConfigurationInternal configuration) {
        BuildableLocalConfigurationMetadata buildableLocalConfigurationMetadata = localComponentMetadataBuilder.addConfiguration(metadata, configuration);
        if (configuration.getResolutionStrategy().isDependencyLockingEnabled()) {
            buildableLocalConfigurationMetadata.enableLocking();
        }
    }

    @Override
    public RootComponentMetadataBuilder withConfigurationsProvider(ConfigurationsProvider alternateProvider) {
        return new DefaultRootComponentMetadataBuilder(metadataProvider, componentIdentifierFactory, moduleIdentifierFactory, localComponentMetadataBuilder, alternateProvider, projectStateRegistry, dependencyLockingProvider);
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
                if (cachedValue.getId().equals(id)) {
                    return cachedValue;
                }
                cachedValue = null;
            }
            return null;
        }
    }
}
