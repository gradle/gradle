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

import com.google.common.base.Optional;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentMetadata, LocalComponentMetadata> implements LocalComponentGraphResolveState {
    private final ConcurrentMap<LocalConfigurationGraphResolveMetadata, DefaultLocalVariantArtifactResolveState> variants = new ConcurrentHashMap<>();

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return getMetadata().getModuleVersionId();
    }

    @Override
    public LocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        return getMetadata().copy(componentIdentifier, artifacts);
    }

    public DefaultLocalComponentGraphResolveState(LocalComponentMetadata metadata) {
        super(metadata, metadata);
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return stateFor((LocalConfigurationGraphResolveMetadata) variant);
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        return stateFor((LocalConfigurationGraphResolveMetadata) variant);
    }

    private DefaultLocalVariantArtifactResolveState stateFor(LocalConfigurationGraphResolveMetadata variant) {
        return variants.computeIfAbsent(variant, c -> new DefaultLocalVariantArtifactResolveState(getMetadata(), variant));
    }

    private static class DefaultLocalVariantArtifactResolveState implements VariantArtifactResolveState, VariantArtifactGraphResolveMetadata {
        private final LocalComponentMetadata component;
        private final LocalConfigurationGraphResolveMetadata graphSelectedVariant;

        public DefaultLocalVariantArtifactResolveState(LocalComponentMetadata component, LocalConfigurationGraphResolveMetadata graphSelectedVariant) {
            this.component = component;
            this.graphSelectedVariant = graphSelectedVariant;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            return graphSelectedVariant.prepareToResolveArtifacts().getArtifacts();
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedVariant.prepareToResolveArtifacts().artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            LocalConfigurationMetadata configuration = graphSelectedVariant.prepareToResolveArtifacts();
            Set<? extends VariantResolveMetadata> fallbackVariants = configuration.getVariants();
            Optional<List<? extends VariantGraphResolveMetadata>> variantsForGraphTraversal = component.getVariantsForGraphTraversal();
            return artifactSelector.resolveArtifacts(component, () -> buildAllVariants(fallbackVariants, variantsForGraphTraversal), fallbackVariants, exclusions, overriddenAttributes);
        }

        private static Set<? extends VariantResolveMetadata> buildAllVariants(Set<? extends VariantResolveMetadata> fallbackVariants, Optional<List<? extends VariantGraphResolveMetadata>> variantsForGraphTraversal) {
            final Set<? extends VariantResolveMetadata> allVariants;
            if (variantsForGraphTraversal.isPresent()) {
                allVariants = variantsForGraphTraversal.get().stream().
                    map(LocalConfigurationGraphResolveMetadata.class::cast).
                    map(LocalConfigurationGraphResolveMetadata::prepareToResolveArtifacts).
                    flatMap(variant -> variant.getVariants().stream()).
                    collect(Collectors.toSet());
            } else {
                allVariants = fallbackVariants;
            }
            return allVariants;
        }
    }
}
