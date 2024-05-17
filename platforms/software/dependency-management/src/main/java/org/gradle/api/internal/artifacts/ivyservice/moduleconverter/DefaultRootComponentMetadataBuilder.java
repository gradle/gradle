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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.ref.SoftReference;

public class DefaultRootComponentMetadataBuilder implements RootComponentMetadataBuilder, HoldsProjectState {
    private final DependencyMetaDataProvider metadataProvider;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;
    private final ConfigurationsProvider configurationsProvider;
    private final MetadataHolder holder;
    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final DefaultRootComponentMetadataBuilder.Factory factory;

    /**
     * Use {@link Factory#create} to create instances.
     */
    private DefaultRootComponentMetadataBuilder(
        DependencyMetaDataProvider metadataProvider,
        ComponentIdentifierFactory componentIdentifierFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        LocalConfigurationMetadataBuilder configurationMetadataBuilder,
        ConfigurationsProvider configurationsProvider,
        ProjectStateRegistry projectStateRegistry,
        LocalComponentGraphResolveStateFactory localResolveStateFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        Factory factory
    ) {
        this.metadataProvider = metadataProvider;
        this.componentIdentifierFactory = componentIdentifierFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.configurationMetadataBuilder = configurationMetadataBuilder;
        this.configurationsProvider = configurationsProvider;
        this.projectStateRegistry = projectStateRegistry;
        this.localResolveStateFactory = localResolveStateFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.factory = factory;
        this.holder = new MetadataHolder();
    }

    @Override
    public RootComponentState toRootComponent(String configurationName) {
        Module module = metadataProvider.getModule();
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module);
        LocalComponentGraphResolveState state = getComponentState(module, componentIdentifier);
        ConfigurationGraphResolveState configuration = state.getConfiguration(configurationName);
        if (configuration == null) {
            throw new IllegalArgumentException(String.format("Expected configuration '%s' to be present in %s", configurationName, componentIdentifier));
        }
        VariantGraphResolveState rootVariant = configuration.asVariant();

        return new RootComponentState() {
            @Override
            public LocalComponentGraphResolveState getRootComponent() {
                return state;
            }

            @Override
            public String getRootConfigurationName() {
                return configurationName;
            }

            @Override
            public VariantGraphResolveState getRootVariant() {
                return rootVariant;
            }
        };
    }

    private LocalComponentGraphResolveState getComponentState(Module module, ComponentIdentifier componentIdentifier) {
        LocalComponentGraphResolveState state = holder.tryCached(componentIdentifier);
        if (state == null) {
            state = createComponentState(module, componentIdentifier);
            holder.cache(state, shouldCacheResolutionState());
        }
        return state;
    }

    private LocalComponentGraphResolveState createComponentState(Module module, ComponentIdentifier componentIdentifier) {
        ProjectComponentIdentifier projectId = module.getProjectId();
        if (projectId != null) {
            ProjectState projectState = projectStateRegistry.stateFor(projectId);
            if (!projectState.hasMutableState()) {
                throw new IllegalStateException("Thread should hold project lock for " + projectState.getDisplayName());
            }
            return projectState.fromMutableState(project -> {
                LocalComponentGraphResolveState state = createProjectRootComponentMetadata(project, module, componentIdentifier);
                // This should move into the configuration metadata builder
                configurationsProvider.visitAll(ConfigurationInternal::preventFromFurtherMutation);
                return state;
            });
        } else {
            return createRootComponentMetadata(module, componentIdentifier, EmptySchema.INSTANCE, RootScriptDomainObjectContext.INSTANCE);
        }
    }

    private LocalComponentGraphResolveState createProjectRootComponentMetadata(ProjectInternal project, Module module, ComponentIdentifier componentIdentifier) {
        return createRootComponentMetadata(module, componentIdentifier, (AttributesSchemaInternal) project.getDependencies().getAttributesSchema(), project.getModel());
    }

    private LocalComponentGraphResolveState createRootComponentMetadata(Module module, ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ModelContainer<?> model) {
        ModuleVersionIdentifier moduleVersionIdentifier = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        DefaultLocalComponentMetadata.ConfigurationsProviderMetadataFactory configurationMetadataFactory =
            new DefaultLocalComponentMetadata.ConfigurationsProviderMetadataFactory(
                configurationsProvider, configurationMetadataBuilder, model, calculatedValueContainerFactory);

        configurationsProvider.visitAll(ConfigurationInternal::preventFromFurtherMutation);

        LocalComponentMetadata metadata = new DefaultLocalComponentMetadata(moduleVersionIdentifier, componentIdentifier, module.getStatus(), schema, configurationMetadataFactory, null);
        if (shouldCacheResolutionState()) {
            return localResolveStateFactory.stateFor(metadata);
        } else {
            // Mark the state as 'ad hoc' and not cacheable
            return localResolveStateFactory.adHocStateFor(metadata);
        }
    }

    private boolean shouldCacheResolutionState() {
        // When there may be more than one configuration defined, cache the component resolution state, so it can be reused for resolving multiple configurations.
        // When there may be no more than one configuration, don't cache the resolution state for reuse. Currently, this only applies to detached configurations, however
        // it might be better to skip caching when it's likely there is only one configuration defined, for example, for script class paths, as the meta-data is unlikely to be reused.
        return !configurationsProvider.isFixedSize() || configurationsProvider.size() > 1;
    }

    @Override
    public RootComponentMetadataBuilder withConfigurationsProvider(ConfigurationsProvider provider) {
        return factory.create(provider);
    }

    public MutationValidator getValidator() {
        return holder;
    }

    @Override
    public void discardAll() {
        holder.discard();
    }

    private static class MetadataHolder implements MutationValidator {
        @Nullable
        private SoftReference<LocalComponentGraphResolveState> reference;
        @Nullable
        private LocalComponentGraphResolveState cachedValue;

        @Override
        public void validateMutation(MutationType type) {
            if (type == MutationType.DEPENDENCIES || type == MutationType.ARTIFACTS ||
                type == MutationType.DEPENDENCY_ATTRIBUTES || type == MutationType.USAGE ||
                type == MutationType.HIERARCHY
            ) {
                LocalComponentGraphResolveState value = currentValue();
                if (value != null) {
                    value.reevaluate();
                }
            }
        }

        @Nullable
        LocalComponentGraphResolveState tryCached(ComponentIdentifier id) {
            LocalComponentGraphResolveState value = currentValue();
            assert value == null || value.getId().equals(id);
            return value;
        }

        @Nullable
        private LocalComponentGraphResolveState currentValue() {
            if (reference != null) {
                return reference.get();
            } else {
                return cachedValue;
            }
        }

        public void discard() {
            reference = null;
            cachedValue = null;
        }

        public void cache(LocalComponentGraphResolveState state, boolean useStrongReference) {
            if (useStrongReference) {
                // Keep a hard reference to the state for re-evaluation on mutation
                // and to force the value to be reused for resolution of other configurations
                reference = null;
                cachedValue = state;
            } else {
                // Keep a soft reference only, as there are no other configurations that will be resolved
                // Need to keep a reference so that the cached value can be re-evaluated on mutation, but
                // use a soft reference to allow the state to be GCed
                //
                // Also keep a soft reference to try to avoid recreating the state when it is queried during
                // work graph calculation and then later during resolution. It would be better if the configuration
                // implementation took care of this instead
                reference = new SoftReference<>(state);
                cachedValue = null;
            }
        }
    }

    public static class Factory {
        private final DependencyMetaDataProvider metaDataProvider;
        private final ComponentIdentifierFactory componentIdentifierFactory;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;
        private final ProjectStateRegistry projectStateRegistry;
        private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;

        @Inject
        public Factory(
            DependencyMetaDataProvider metaDataProvider,
            ComponentIdentifierFactory componentIdentifierFactory,
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            LocalConfigurationMetadataBuilder configurationMetadataBuilder,
            ProjectStateRegistry projectStateRegistry,
            LocalComponentGraphResolveStateFactory localResolveStateFactory,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            this.metaDataProvider = metaDataProvider;
            this.componentIdentifierFactory = componentIdentifierFactory;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.configurationMetadataBuilder = configurationMetadataBuilder;
            this.projectStateRegistry = projectStateRegistry;
            this.localResolveStateFactory = localResolveStateFactory;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        }

        public RootComponentMetadataBuilder create(ConfigurationsProvider configurationsProvider) {
            return new DefaultRootComponentMetadataBuilder(
                metaDataProvider,
                componentIdentifierFactory,
                moduleIdentifierFactory,
                configurationMetadataBuilder,
                configurationsProvider,
                projectStateRegistry,
                localResolveStateFactory,
                calculatedValueContainerFactory,
                this
            );
        }
    }
}
