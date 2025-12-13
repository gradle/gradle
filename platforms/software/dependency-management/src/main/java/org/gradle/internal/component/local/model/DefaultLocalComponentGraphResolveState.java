/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 * <p>The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentGraphResolveMetadata> implements LocalComponentGraphResolveState {

    private final boolean adHoc;

    // The variants to use for variant selection during graph resolution
    private final CalculatedValue<LocalComponentGraphSelectionCandidates> graphSelectionCandidates;

    public DefaultLocalComponentGraphResolveState(
        long instanceId,
        LocalComponentGraphResolveMetadata metadata,
        AttributeDesugaring attributeDesugaring,
        boolean adHoc,
        LocalVariantGraphResolveStateFactory variantFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        super(instanceId, metadata, attributeDesugaring);
        this.adHoc = adHoc;

        this.graphSelectionCandidates = calculatedValueContainerFactory.create(Describables.of("variants of", getMetadata()), context ->
            computeGraphSelectionCandidates(variantFactory)
        );
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return getMetadata().getModuleVersionId();
    }

    @Override
    public boolean isAdHoc() {
        return adHoc;
    }

    @Override
    public ComponentArtifactResolveMetadata getArtifactMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public LocalComponentGraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        graphSelectionCandidates.finalizeIfNotAlready();
        return graphSelectionCandidates.get();
    }

    private static LocalComponentGraphSelectionCandidates computeGraphSelectionCandidates(
        LocalVariantGraphResolveStateFactory variantFactory
    ) {
        ImmutableList.Builder<LocalVariantGraphResolveState> variantsWithAttributes = new ImmutableList.Builder<>();
        ImmutableMap.Builder<String, LocalVariantGraphResolveState> variantsByConfigurationName = ImmutableMap.builder();

        variantFactory.visitConsumableVariants(variantState -> {
            if (!variantState.getAttributes().isEmpty()) {
                variantsWithAttributes.add(variantState);
            }

            if (variantState.getMetadata().getConfigurationName() != null) {
                variantsByConfigurationName.put(variantState.getMetadata().getConfigurationName(), variantState);
            }
        });

        return new DefaultLocalComponentGraphSelectionCandidates(
            variantsWithAttributes.build(),
            variantsByConfigurationName.build()
        );
    }

    private static class LocalComponentArtifactResolveMetadata implements ComponentArtifactResolveMetadata {
        private final ComponentGraphResolveMetadata metadata;

        public LocalComponentArtifactResolveMetadata(ComponentGraphResolveMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public ComponentIdentifier getId() {
            return metadata.getId();
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return metadata.getModuleVersionId();
        }

        @Override
        public ModuleSources getSources() {
            return ImmutableModuleSources.of();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public ImmutableAttributesSchema getAttributesSchema() {
            return metadata.getAttributesSchema();
        }
    }

    private static class DefaultLocalComponentGraphSelectionCandidates implements LocalComponentGraphSelectionCandidates {
        private final List<? extends LocalVariantGraphResolveState> variantsWithAttributes;
        private final Map<String, LocalVariantGraphResolveState> variantsByConfigurationName;

        public DefaultLocalComponentGraphSelectionCandidates(
            List<? extends LocalVariantGraphResolveState> variantsWithAttributes,
            Map<String, LocalVariantGraphResolveState> variantsByConfigurationName
        ) {
            this.variantsWithAttributes = variantsWithAttributes;
            this.variantsByConfigurationName = variantsByConfigurationName;
        }

        @Override
        public List<? extends LocalVariantGraphResolveState> getVariantsForAttributeMatching() {
            return variantsWithAttributes;
        }

        @Nullable
        @Override
        public VariantGraphResolveState getLegacyVariant() {
            return getVariantByConfigurationName(Dependency.DEFAULT_CONFIGURATION);
        }

        @Nullable
        @Override
        public LocalVariantGraphResolveState getVariantByConfigurationName(String name) {
            return variantsByConfigurationName.get(name);
        }

        @Override
        public List<LocalVariantGraphResolveState> getAllSelectableVariants() {
            // Find the names of all selectable variants that are not in the variantsWithAttributes
            Set<String> configurationNames = new HashSet<>(variantsByConfigurationName.keySet());
            for (LocalVariantGraphResolveState variant : variantsWithAttributes) {
                if (variant.getMetadata().getConfigurationName() != null) {
                    configurationNames.remove(variant.getMetadata().getConfigurationName());
                }
            }

            // Join the list of variants with attributes with the list of variants by configuration name
            List<LocalVariantGraphResolveState> result = new ArrayList<>(variantsWithAttributes);
            for (String configurationName : configurationNames) {
                result.add(variantsByConfigurationName.get(configurationName));
            }

            return result;
        }
    }

}
