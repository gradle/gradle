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
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.ref.SoftReference;

public class DefaultRootComponentMetadataBuilder implements RootComponentMetadataBuilder, HoldsProjectState {
    private final DomainObjectContext owner;
    private final DependencyMetaDataProvider componentIdentity;
    private final ConfigurationsProvider configurationsProvider;
    private final AttributesSchemaInternal schema;

    // Services
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
    private final ImmutableAttributesSchemaFactory attributesSchemaFactory;
    private final DefaultRootComponentMetadataBuilder.Factory factory;

    // State
    private final MetadataHolder holder;

    /**
     * Use {@link Factory#create} to create instances.
     */
    private DefaultRootComponentMetadataBuilder(
        DomainObjectContext owner,
        DependencyMetaDataProvider componentIdentity,
        ConfigurationsProvider configurationsProvider,
        AttributesSchemaInternal schema,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        LocalComponentGraphResolveStateFactory localResolveStateFactory,
        ImmutableAttributesSchemaFactory attributesSchemaFactory,
        Factory factory
    ) {
        this.owner = owner;
        this.componentIdentity = componentIdentity;
        this.configurationsProvider = configurationsProvider;
        this.schema = schema;

        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.localResolveStateFactory = localResolveStateFactory;
        this.attributesSchemaFactory = attributesSchemaFactory;
        this.factory = factory;

        this.holder = new MetadataHolder();
    }

    @Override
    public RootComponentState toRootComponent(String configurationName) {
        Module module = componentIdentity.getModule();
        String status = module.getStatus();
        ComponentIdentifier componentIdentifier = module.getComponentId();
        ModuleVersionIdentifier moduleVersionId = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getName(), module.getVersion());
        ImmutableAttributesSchema immutableSchema = attributesSchemaFactory.create(schema);

        LocalComponentGraphResolveMetadata metadata = new LocalComponentGraphResolveMetadata(
            moduleVersionId,
            componentIdentifier,
            status,
            immutableSchema
        );

        LocalComponentGraphResolveState rootComponent = getComponentState(owner, metadata);

        return new RootComponentState() {
            @Override
            public LocalComponentGraphResolveState getRootComponent() {
                return rootComponent;
            }

            @Override
            public LocalVariantGraphResolveState getRootVariant() {
                // TODO: It would be nice if we could calculate the rootVariant once, but it is possible
                // that the root component changes between build dependency resolution and complete
                // graph resolution. In 9.0, these changes will be forbidden.

                // TODO: We should not ask the component for a resolvable configuration. Components should only
                // expose variants -- which are by definition consumable only. Instead, we should create our own
                // root variant and add it to a new one-off root component that holds only that root variant.
                // The root variant should not live in a standard local component alongside other (consumable) variants.
                @SuppressWarnings("deprecation")
                LocalVariantGraphResolveState rootVariant = rootComponent.getConfigurationLegacy(configurationName);
                if (rootVariant == null) {
                    throw new IllegalStateException(String.format("Expected root variant '%s' to be present in %s", configurationName, componentIdentifier));
                }
                return rootVariant;
            }
        };
    }

    @Override
    public DependencyMetaDataProvider getComponentIdentity() {
        return componentIdentity;
    }

    private LocalComponentGraphResolveState getComponentState(
        DomainObjectContext domainObjectContext,
        LocalComponentGraphResolveMetadata metadata
    ) {
        LocalComponentGraphResolveState state = holder.tryCached(metadata.getId());
        if (state != null) {
            return state;
        }

        LocalComponentGraphResolveState result;
        ModelContainer<?> model = domainObjectContext.getModel();

        if (shouldCacheResolutionState()) {
            result = localResolveStateFactory.stateFor(model, metadata, configurationsProvider);
            holder.cache(result, true);
        } else {
            // Mark the state as 'ad hoc' and not cacheable
            result = localResolveStateFactory.adHocStateFor(model, metadata, configurationsProvider);
            holder.cache(result, false);
        }

        return result;
    }

    private boolean shouldCacheResolutionState() {
        // When there may be more than one configuration defined, cache the component resolution state, so it can be reused for resolving multiple configurations.
        // When there may be no more than one configuration, don't cache the resolution state for reuse. Currently, this only applies to detached configurations, however
        // it might be better to skip caching when it's likely there is only one configuration defined, for example, for script class paths, as the meta-data is unlikely to be reused.
        return !configurationsProvider.isFixedSize() || configurationsProvider.size() > 1;
    }

    @Override
    public RootComponentMetadataBuilder newBuilder(DependencyMetaDataProvider identity, ConfigurationsProvider provider) {
        return factory.create(owner, provider, identity, schema);
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
        private final LocalComponentGraphResolveStateFactory localResolveStateFactory;
        private final ImmutableAttributesSchemaFactory attributesSchemaFactory;

        @Inject
        public Factory(
            ImmutableModuleIdentifierFactory moduleIdentifierFactory,
            LocalComponentGraphResolveStateFactory localResolveStateFactory,
            ImmutableAttributesSchemaFactory attributesSchemaFactory
        ) {
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.localResolveStateFactory = localResolveStateFactory;
            this.attributesSchemaFactory = attributesSchemaFactory;
        }

        public RootComponentMetadataBuilder create(
            DomainObjectContext owner,
            ConfigurationsProvider configurationsProvider,
            DependencyMetaDataProvider componentIdentity,
            AttributesSchemaInternal schema
        ) {
            return new DefaultRootComponentMetadataBuilder(
                owner,
                componentIdentity,
                configurationsProvider,
                schema,
                moduleIdentifierFactory,
                localResolveStateFactory,
                attributesSchemaFactory,
                this
            );
        }
    }
}
