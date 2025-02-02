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
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.gradle.internal.model.InMemoryCacheFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 * <p>The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentGraphResolveMetadata> implements LocalComponentGraphResolveState {

    private final ComponentIdGenerator idGenerator;
    private final boolean adHoc;
    private final LocalVariantGraphResolveStateFactory variantFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final InMemoryCacheFactory cacheFactory;
    private final ComponentIdentifier overrideComponentId;

    // The graph resolve state for variants selected by name
    private final InMemoryLoadingCache<String, LocalVariantGraphResolveState> variants;

    // The variants to use for variant selection during graph resolution
    private final AtomicReference<CalculatedValue<LocalComponentGraphSelectionCandidates>> graphSelectionCandidates = new AtomicReference<>();

    // The public view of all selectable variants of this component
    private final AtomicReference<CalculatedValue<List<ResolvedVariantResult>>> selectableVariantResults = new AtomicReference<>();

    public DefaultLocalComponentGraphResolveState(
        long instanceId,
        LocalComponentGraphResolveMetadata metadata,
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        boolean adHoc,
        LocalVariantGraphResolveStateFactory variantFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        InMemoryCacheFactory cacheFactory,
        @Nullable ComponentIdentifier overrideComponentId
    ) {
        super(instanceId, metadata, attributeDesugaring);
        this.idGenerator = idGenerator;
        this.adHoc = adHoc;
        this.variantFactory = variantFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.cacheFactory = cacheFactory;
        this.overrideComponentId = overrideComponentId;

        // Mutable state
        this.variants = cacheFactory.createCalculatedValueCache(Describables.of("variants"), this::doCreateLegacyConfiguration);
        initCalculatedValues();
    }

    @Override
    public void reevaluate() {
        // TODO: This is not really thread-safe.
        //       We should atomically clear all the different fields at once.
        //       Or better yet, we should not allow reevaluation of the state.
        variants.invalidate();
        variantFactory.invalidate();
        initCalculatedValues();
    }

    private void initCalculatedValues() {
        // TODO: We wrap the CalculatedValues in an AtomicReference so that we can reset their state, however
        //       CalculatedValues are not resettable for a reason. This is a pretty terrible hack.
        //       We should get rid of reevaluate entirely, so that we do not need these AtomicReferences.
        //       We are already on this path -- we deprecated mutating a configuration after observation.
        //       However, while mutation is still allowed, we need hacks like this, as plugins are relying
        //       on the deprecated behavior, for example the Spring dependency management plugin which adds
        //       excludes to dependencies in a beforeResolve.
        this.graphSelectionCandidates.set(
            calculatedValueContainerFactory.create(Describables.of("variants of", getMetadata()), context ->
                computeGraphSelectionCandidates(variantFactory, overrideComponentId)
            )
        );
        this.selectableVariantResults.set(
            calculatedValueContainerFactory.create(Describables.of("public variants of", getMetadata()), context ->
                computeSelectableVariantResults(this)
            )
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
    public LocalComponentGraphResolveState copyWithComponentId(ComponentIdentifier overrideComponentId) {
        LocalComponentGraphResolveMetadata originalMetadata = getMetadata();
        LocalComponentGraphResolveMetadata copiedMetadata = new LocalComponentGraphResolveMetadata(
            originalMetadata.getModuleVersionId(),
            overrideComponentId,
            originalMetadata.getStatus(),
            originalMetadata.getAttributesSchema()
        );

        return new DefaultLocalComponentGraphResolveState(
            idGenerator.nextComponentId(),
            copiedMetadata,
            getAttributeDesugaring(),
            idGenerator,
            adHoc,
            variantFactory,
            calculatedValueContainerFactory,
            cacheFactory,
            overrideComponentId
        );
    }

    @Override
    public ComponentArtifactResolveMetadata getArtifactMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public LocalComponentGraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        CalculatedValue<LocalComponentGraphSelectionCandidates> value = graphSelectionCandidates.get();
        value.finalizeIfNotAlready();
        return value.get();
    }

    private static LocalComponentGraphSelectionCandidates computeGraphSelectionCandidates(
        LocalVariantGraphResolveStateFactory variantFactory,
        @Nullable ComponentIdentifier overrideComponentId
    ) {
        ImmutableList.Builder<LocalVariantGraphResolveState> variantsWithAttributes = new ImmutableList.Builder<>();
        ImmutableMap.Builder<String, LocalVariantGraphResolveState> variantsByConfigurationName = ImmutableMap.builder();

        variantFactory.visitConsumableVariants(variantState -> {
            if (overrideComponentId != null) {
                variantState = variantState.copyWithComponentId(overrideComponentId);
            }

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

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        CalculatedValue<List<ResolvedVariantResult>> value = selectableVariantResults.get();
        value.finalizeIfNotAlready();
        return value.get();
    }

    private static List<ResolvedVariantResult> computeSelectableVariantResults(DefaultLocalComponentGraphResolveState component) {
        return component.getCandidatesForGraphVariantSelection()
            .getVariantsForAttributeMatching()
            .stream()
            .flatMap(variant -> variant.prepareForArtifactResolution().getArtifactVariants().stream())
            .map(variant -> new DefaultResolvedVariantResult(
                component.getId(),
                Describables.of(variant.getName()),
                component.getAttributeDesugaring().desugar(variant.getAttributes().asImmutable()),
                component.capabilitiesFor(variant.getCapabilities()),
                null
            ))
            .collect(Collectors.toList());
    }

    @Nullable
    @Override
    @Deprecated
    public LocalVariantGraphResolveState getConfigurationLegacy(String configurationName) {
        return variants.get(configurationName);
    }

    private @Nullable LocalVariantGraphResolveState doCreateLegacyConfiguration(String n) {
        LocalVariantGraphResolveState variant = variantFactory.getVariantByConfigurationName(n);
        if (variant == null) {
            return null;
        }
        if (overrideComponentId != null) {
            return variant.copyWithComponentId(overrideComponentId);
        }
        return variant;
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
