/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.external.model;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.List;

abstract class AbstractLazyModuleComponentResolveMetadata extends AbstractModuleComponentResolveMetadata {
    private final VariantMetadataRules variantMetadataRules;

    private Optional<ImmutableList<? extends ConfigurationMetadata>> graphVariants;

    AbstractLazyModuleComponentResolveMetadata(AbstractMutableModuleComponentResolveMetadata metadata) {
        super(metadata);
        variantMetadataRules = metadata.getVariantMetadataRules();
    }

    /**
     * Creates a copy of the given metadata
     */
    AbstractLazyModuleComponentResolveMetadata(AbstractLazyModuleComponentResolveMetadata metadata, @Nullable ModuleSource source) {
        super(metadata, source);
        variantMetadataRules = metadata.variantMetadataRules;
    }

    /**
     * Clear any cached state, for the case where the inputs are invalidated.
     * This only happens when constructing a copy
     */
    protected void copyCachedState(AbstractLazyModuleComponentResolveMetadata metadata) {
        super.copyCachedState(metadata);
        this.graphVariants = metadata.graphVariants;
    }

    @Override
    protected final DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy) {
        return createConfiguration(componentId, name, transitive, visible, hierarchy, variantMetadataRules);
    }

    /**
     * Creates a {@link org.gradle.internal.component.model.ConfigurationMetadata} implementation for this component.
     */
    protected abstract DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy, VariantMetadataRules componentMetadataRules);

    private Optional<ImmutableList<? extends ConfigurationMetadata>> buildVariantsForGraphTraversal(List<? extends ComponentVariant> variants) {
        if (variants.isEmpty()) {
            return maybeDeriveVariants();
        }
        ImmutableList.Builder<ConfigurationMetadata> configurations = new ImmutableList.Builder<ConfigurationMetadata>();
        for (ComponentVariant variant : variants) {
            configurations.add(new LazyVariantBackedConfigurationMetadata(getId(), variant, getAttributes(), getAttributesFactory(), variantMetadataRules));
        }
        return Optional.<ImmutableList<? extends ConfigurationMetadata>>of(configurations.build());
    }

    @Override
    public synchronized Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal(getVariants());
        }
        return graphVariants;
    }

}
