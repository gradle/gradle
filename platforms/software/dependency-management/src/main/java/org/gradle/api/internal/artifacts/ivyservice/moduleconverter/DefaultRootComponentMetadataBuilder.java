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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.ref.SoftReference;

public class DefaultRootComponentMetadataBuilder implements RootComponentMetadataBuilder, HoldsProjectState {
    private final DependencyMetaDataProvider componentIdentity;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ConfigurationsProvider configurationsProvider;
    private final MetadataHolder holder;
    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
    private final DefaultRootComponentMetadataBuilder.Factory factory;

    /**
     * Use {@link Factory#create} to create instances.
     */
    private DefaultRootComponentMetadataBuilder(
        DependencyMetaDataProvider componentIdentity,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ConfigurationsProvider configurationsProvider,
        ProjectStateRegistry projectStateRegistry,
        LocalComponentGraphResolveStateFactory localResolveStateFactory,
        Factory factory
    ) {
        this.componentIdentity = componentIdentity;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.configurationsProvider = configurationsProvider;
        this.projectStateRegistry = projectStateRegistry;
        this.localResolveStateFactory = localResolveStateFactory;
        this.factory = factory;
        this.holder = new MetadataHolder();
    }

    @Override
    public RootComponentState toRootComponent(String configurationName) {
        Module module = componentIdentity.getModule();
        ComponentIdentifier componentIdentifier = getComponentIdentifier(module);
        ModuleVersionIdentifier moduleVersionId = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());

        return new RootComponentState() {
            @Override
            public LocalComponentGraphResolveState getRootComponent() {
                return getComponentState(module, componentIdentifier, moduleVersionId);
            }

            @Override
            public VariantGraphResolveState getRootVariant() {
                // TODO: We should not ask the component for a resolvable configuration. Components should only
                // expose variants -- which are by definition consumable only. Instead, we should create our own
                // root variant and add it to a new one-off root component that holds only that root variant.
                // The root variant should not live in a standard local component alongside other (consumable) variants.
                @SuppressWarnings("deprecation")
                LocalVariantGraphResolveState rootVariant = getRootComponent().getConfigurationLegacy(configurationName);
                if (rootVariant == null) {
                    throw new IllegalArgumentException(String.format("Expected root variant '%s' to be present in %s", configurationName, componentIdentifier));
                }
                return rootVariant;
            }

            @Override
            public ComponentIdentifier getComponentIdentifier() {
                return componentIdentifier;
            }

            @Override
            public ModuleVersionIdentifier getModuleVersionIdentifier() {
                return moduleVersionId;
            }
        };
    }

    private static ComponentIdentifier getComponentIdentifier(Module module) {
        ComponentIdentifier componentIdentifier = module.getComponentId();
        if (componentIdentifier != null) {
            return componentIdentifier;
        }

        return new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(
            module.getGroup(), module.getName()), module.getVersion()
        );
    }

    @Override
    public DependencyMetaDataProvider getComponentIdentity() {
        return componentIdentity;
    }

    private LocalComponentGraphResolveState getComponentState(
        Module module,
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionIdentifier
    ) {
        LocalComponentGraphResolveState state = holder.tryCached(componentIdentifier);
        if (state == null) {
            state = createComponentState(module, componentIdentifier, moduleVersionIdentifier);
            holder.cache(state, shouldCacheResolutionState());
        }
        return state;
    }

    private LocalComponentGraphResolveState createComponentState(
        Module module,
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId
    ) {
        String status = module.getStatus();
        ProjectComponentIdentifier projectId = module.getOwner();

        if (projectId != null) {
            ProjectState projectState = projectStateRegistry.stateFor(projectId);
            if (!projectState.hasMutableState()) {
                throw new IllegalStateException("Thread should hold project lock for " + projectState.getDisplayName());
            }
            return projectState.fromMutableState(project -> {
                AttributesSchemaInternal schema = (AttributesSchemaInternal) project.getDependencies().getAttributesSchema();
                return createRootComponentMetadata(componentIdentifier, moduleVersionId, status, schema, project.getModel());
            });
        } else {
            return createRootComponentMetadata(componentIdentifier, moduleVersionId, status, EmptySchema.INSTANCE, RootScriptDomainObjectContext.INSTANCE);
        }
    }

    private LocalComponentGraphResolveState createRootComponentMetadata(
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        ModelContainer<?> model
    ) {
        if (shouldCacheResolutionState()) {
            return localResolveStateFactory.stateFor(model, componentIdentifier, moduleVersionId, configurationsProvider, status, schema);
        } else {
            // Mark the state as 'ad hoc' and not cacheable
            return localResolveStateFactory.adHocStateFor(model, componentIdentifier, moduleVersionId, configurationsProvider, status, schema);
        }
    }

    private boolean shouldCacheResolutionState() {
        // When there may be more than one configuration defined, cache the component resolution state, so it can be reused for resolving multiple configurations.
        // When there may be no more than one configuration, don't cache the resolution state for reuse. Currently, this only applies to detached configurations, however
        // it might be better to skip caching when it's likely there is only one configuration defined, for example, for script class paths, as the meta-data is unlikely to be reused.
        return !configurationsProvider.isFixedSize() || configurationsProvider.size() > 1;
    }

    @Override
    public RootComponentMetadataBuilder newBuilder(DependencyMetaDataProvider identity, ConfigurationsProvider provider) {
        return factory.create(provider, identity);
    }

    @Override
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
            if (type != MutationType.STRATEGY) {
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
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final ProjectStateRegistry projectStateRegistry;
        private final LocalComponentGraphResolveStateFactory localResolveStateFactory;

        @Inject
        public Factory(
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            ProjectStateRegistry projectStateRegistry,
            LocalComponentGraphResolveStateFactory localResolveStateFactory
        ) {
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.projectStateRegistry = projectStateRegistry;
            this.localResolveStateFactory = localResolveStateFactory;
        }

        public RootComponentMetadataBuilder create(ConfigurationsProvider configurationsProvider, DependencyMetaDataProvider componentIdentity) {
            return new DefaultRootComponentMetadataBuilder(
                componentIdentity,
                moduleIdentifierFactory,
                configurationsProvider,
                projectStateRegistry,
                localResolveStateFactory,
                this
            );
        }
    }
}
