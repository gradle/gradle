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

package org.gradle.internal.component.external.model.ivy;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * External component state implementation for ivy components.
 */
public class DefaultIvyComponentGraphResolveState extends DefaultExternalModuleComponentGraphResolveState<IvyModuleResolveMetadata, IvyModuleResolveMetadata> implements IvyComponentGraphResolveState {

    public DefaultIvyComponentGraphResolveState(long instanceId, IvyModuleResolveMetadata metadata, AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator) {
        super(instanceId, metadata, metadata, attributeDesugaring, idGenerator);
    }

    @Override
    public Set<String> getConfigurationNames() {
        return getMetadata().getConfigurationNames();
    }

    @Nullable
    @Override
    public ConfigurationGraphResolveState getConfiguration(String configurationName) {
        ModuleConfigurationMetadata configuration = getMetadata().getConfiguration(configurationName);
        if (configuration == null) {
            return null;
        } else {
            return resolveStateFor(configuration);
        }
    }

    @Nullable
    private VariantGraphResolveState getConfigurationAsVariant(String name) {
        ConfigurationGraphResolveState configuration = getConfiguration(name);
        if (configuration == null) {
            return null;
        }
        return configuration.asVariant();
    }


    @Override
    public IvyComponentArtifactResolveMetadata getArtifactMetadata() {
        @SuppressWarnings("deprecation")
        IvyModuleResolveMetadata legacyMetadata = getLegacyMetadata();
        return new DefaultIvyComponentArtifactResolveMetadata(legacyMetadata);
    }

    @Override
    public GraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        return new IvyGraphSelectionCandidates(
            super.getCandidatesForGraphVariantSelection(),
            this::getConfigurationAsVariant
        );
    }

    private static class DefaultIvyComponentArtifactResolveMetadata extends ExternalArtifactResolveMetadata implements IvyComponentArtifactResolveMetadata {
        private final IvyModuleResolveMetadata metadata;

        public DefaultIvyComponentArtifactResolveMetadata(IvyModuleResolveMetadata metadata) {
            super(metadata);
            this.metadata = metadata;
        }

        @Override
        @Nullable
        public ImmutableList<? extends ComponentArtifactMetadata> getConfigurationArtifacts(String configurationName) {
            ConfigurationMetadata configuration = metadata.getConfiguration(configurationName);
            if (configuration != null) {
                return configuration.getArtifacts();
            }
            return null;
        }
    }

    private static class IvyGraphSelectionCandidates implements GraphSelectionCandidates {
        private final GraphSelectionCandidates candidates;
        private final Function<String, VariantGraphResolveState> configurationSupplier;

        public IvyGraphSelectionCandidates(
            GraphSelectionCandidates candidates,
            Function<String, VariantGraphResolveState> configurationSupplier
        ) {
            this.candidates = candidates;
            this.configurationSupplier = configurationSupplier;
        }

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            return candidates.getVariantsForAttributeMatching();
        }

        @Nullable
        @Override
        public VariantGraphResolveState getLegacyVariant() {
            return getVariantByConfigurationName(Dependency.DEFAULT_CONFIGURATION);
        }

        @Nullable
        @Override
        public VariantGraphResolveState getVariantByConfigurationName(String name) {
            return configurationSupplier.apply(name);
        }
    }

}
