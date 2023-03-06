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
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.inject.Inject;

public class DefaultRootComponentMetadataBuilder implements RootComponentMetadataBuilder, HoldsProjectState {
    private final DependencyMetaDataProvider metadataProvider;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalComponentMetadataBuilder localComponentMetadataBuilder;
    private final ConfigurationsProvider configurationsProvider;
    private final MetadataHolder holder;
    private final ProjectStateRegistry projectStateRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final DefaultRootComponentMetadataBuilder.Factory factory;

    /**
     * Use {@link Factory#create} to create instances.
     */
    private DefaultRootComponentMetadataBuilder(
        DependencyMetaDataProvider metadataProvider,
        ComponentIdentifierFactory componentIdentifierFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        LocalComponentMetadataBuilder localComponentMetadataBuilder,
        ConfigurationsProvider configurationsProvider,
        ProjectStateRegistry projectStateRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        Factory factory
    ) {
        this.metadataProvider = metadataProvider;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.localComponentMetadataBuilder = localComponentMetadataBuilder;
        this.configurationsProvider = configurationsProvider;
        this.projectStateRegistry = projectStateRegistry;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.factory = factory;
        this.holder = new MetadataHolder(configurationsProvider);
    }

    @Override
    public LocalComponentMetadata toRootComponentMetaData() {
        Module module = metadataProvider.getModule();
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module);
        LocalComponentMetadata metadata = holder.tryCached(componentIdentifier);
        if (metadata == null) {
            metadata = buildRootComponentMetadata(module, componentIdentifier);
            holder.cachedValue = metadata;
        }
        return metadata;
    }

    private LocalComponentMetadata buildRootComponentMetadata(final Module module, final ComponentIdentifier componentIdentifier) {
        final ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ProjectComponentIdentifier projectId = module.getProjectId();
        if (projectId != null) {
            ProjectState projectState = projectStateRegistry.stateFor(projectId);
            if (!projectState.hasMutableState()) {
                throw new IllegalStateException("Thread should hold project lock for " + projectId);
            }
            return projectState.fromMutableState(project -> {
                AttributesSchemaInternal schema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
                return getRootComponentMetadata(module, componentIdentifier, moduleVersionIdentifier, schema, projectState);
            });
        } else {
            return getRootComponentMetadata(module, componentIdentifier, moduleVersionIdentifier, EmptySchema.INSTANCE, RootScriptDomainObjectContext.INSTANCE);
        }
    }

    private LocalComponentMetadata getRootComponentMetadata(Module module, ComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, AttributesSchemaInternal schema, ModelContainer<?> model) {
        DefaultLocalComponentMetadata metadata = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), schema, model, calculatedValueContainerFactory);
        for (ConfigurationInternal configuration : configurationsProvider.getAll()) {
            addConfiguration(metadata, configuration);
        }
        return metadata;
    }

    private void addConfiguration(DefaultLocalComponentMetadata metadata, ConfigurationInternal configuration) {
        localComponentMetadataBuilder.addConfiguration(metadata, configuration);
    }

    @Override
    public RootComponentMetadataBuilder withConfigurationsProvider(ConfigurationsProvider alternateProvider) {
        return factory.create(alternateProvider);
    }

    public MutationValidator getValidator() {
        return holder;
    }

    @Override
    public void discardAll() {
        holder.discard();
    }

    private static class MetadataHolder implements MutationValidator {
        private LocalComponentMetadata cachedValue;
        private final ConfigurationsProvider configurationsProvider;

        private MetadataHolder(ConfigurationsProvider configurationsProvider) {
            this.configurationsProvider = configurationsProvider;
        }

        @Override
        public void validateMutation(MutationType type) {
            if (type == MutationType.DEPENDENCIES || type == MutationType.ARTIFACTS || type == MutationType.DEPENDENCY_ATTRIBUTES) {
                if (cachedValue != null) {
                    if (cachedValue.getConfigurationNames().size() != configurationsProvider.size()) {
                        // The number of configurations in the project has changed, so we need to regenerate the root component metadata
                        cachedValue = null;
                    } else {
                        cachedValue.reevaluate();
                    }
                }
            } else if (type == MutationType.HIERARCHY) {
                // The hierarchy is provided to the configuration metadata on construction. Since it is not
                // computed lazily, there is no lazy value to invalidate. Thus, we need to recompute the
                // entire component in order to reconstruct new configuration metadatas with new hierarchy values.
                cachedValue = null;
            }
        }

        LocalComponentMetadata tryCached(ComponentIdentifier id) {
            if (cachedValue != null) {
                if (cachedValue.getId().equals(id)) {
                    return cachedValue;
                }
                cachedValue = null;
            }
            return null;
        }

        public void discard() {
            cachedValue = null;
        }
    }


    public static class Factory {
        private final DependencyMetaDataProvider metaDataProvider;
        private final ComponentIdentifierFactory componentIdentifierFactory;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final LocalComponentMetadataBuilder localComponentMetadataBuilder;
        private final ProjectStateRegistry projectStateRegistry;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;

        @Inject
        public Factory(
            DependencyMetaDataProvider metaDataProvider,
            ComponentIdentifierFactory componentIdentifierFactory,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            LocalComponentMetadataBuilder localComponentMetadataBuilder,
            ProjectStateRegistry projectStateRegistry,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            this.metaDataProvider = metaDataProvider;
            this.componentIdentifierFactory = componentIdentifierFactory;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.localComponentMetadataBuilder = localComponentMetadataBuilder;
            this.projectStateRegistry = projectStateRegistry;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        }

        public DefaultRootComponentMetadataBuilder create(ConfigurationsProvider configurationsProvider) {
            return new DefaultRootComponentMetadataBuilder(
                metaDataProvider,
                componentIdentifierFactory,
                moduleIdentifierFactory,
                localComponentMetadataBuilder,
                configurationsProvider,
                projectStateRegistry,
                calculatedValueContainerFactory,
                this
            );
        }
    }
}
