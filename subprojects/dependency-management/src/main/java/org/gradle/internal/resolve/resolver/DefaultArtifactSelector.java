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

package org.gradle.internal.resolve.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactSelectionCandidates;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.component.model.VariantWithOverloadAttributes;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultArtifactSelector implements ArtifactSelector {
    private final List<OriginArtifactSelector> selectors;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    private final ResolvedVariantCache resolvedVariantCache;

    public DefaultArtifactSelector(List<OriginArtifactSelector> selectors, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory, ResolvedVariantCache resolvedVariantCache) {
        this.selectors = selectors;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.resolvedVariantCache = resolvedVariantCache;
    }

    @Override
    public ArtifactSet resolveArtifacts(LocalFileDependencyMetadata fileDependencyMetadata) {
        return new FileDependencyArtifactSet(fileDependencyMetadata, artifactTypeRegistry, calculatedValueContainerFactory);
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentArtifactResolveMetadata component, VariantArtifactSelectionCandidates variant, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
        ImmutableSet<ResolvedVariant> legacyResolvedVariants = buildResolvedVariants(component, variant.getLegacyVariants(), exclusions);
        Lazy<ImmutableSet<ResolvedVariant>> allVariants = Lazy.locking().of(() -> buildResolvedVariants(component, variant.getAllVariants(), exclusions));

        for (OriginArtifactSelector selector : selectors) {
            ArtifactSet artifacts = selector.resolveArtifacts(component, allVariants::get, legacyResolvedVariants, overriddenAttributes);
            if (artifacts != null) {
                return artifacts;
            }
        }
        throw new IllegalStateException("No artifacts selected.");
    }

    private ImmutableSet<ResolvedVariant> buildResolvedVariants(ComponentArtifactResolveMetadata component, Set<? extends VariantResolveMetadata> allVariants, ExcludeSpec exclusions) {
        ImmutableSet.Builder<ResolvedVariant> resolvedVariantBuilder = ImmutableSet.builderWithExpectedSize(allVariants.size());
        for (VariantResolveMetadata variant : allVariants) {
            resolvedVariantBuilder.add(toResolvedVariant(variant, exclusions, component));
        }
        return resolvedVariantBuilder.build();
    }

    private ResolvedVariant toResolvedVariant(
        VariantResolveMetadata variant,
        ExcludeSpec exclusions,
        ComponentArtifactResolveMetadata component
    ) {
        // artifactsToResolve are those not excluded by their owning module
        boolean hasExcludedArtifact = false;
        ImmutableList.Builder<ComponentArtifactMetadata> artifactsToResolveBuilder = ImmutableList.builder();
        for (ComponentArtifactMetadata artifact : variant.getArtifacts()) {
            if (!exclusions.excludesArtifact(component.getModuleVersionId().getModule(), artifact.getName())) {
                artifactsToResolveBuilder.add(artifact);
            } else {
                hasExcludedArtifact = true;
            }
        }
        ImmutableList<ComponentArtifactMetadata> artifactsToResolve = artifactsToResolveBuilder.build();

        DisplayName displayName = variant.asDescribable();
        VariantResolveMetadata.Identifier identifier = variant.getIdentifier();
        ImmutableCapabilities capabilities = withImplicitCapability(variant.getCapabilities(), component);
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant.getAttributes(), artifactsToResolve);

        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            return new ArtifactBackedResolvedVariant(null, displayName, attributes, capabilities, artifactsToResolve, new DefaultComponentArtifactResolver(component, artifactResolver));
        } else if (!variant.isEligibleForCaching()) {
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifactsToResolve, new DefaultComponentArtifactResolver(component, artifactResolver));
        } else {
            // This is a bit of a hack because we allow the artifactType registry to be different in every resolution scope.
            // This means it's not safe to assume a variant resolved in one consumer can be reused in another consumer with the same key.
            // Most of the time the artifactType registry has the same effect on the variant's attributes, but this isn't guaranteed.
            // It might be better to tighten this up by either requiring a single artifactType registry for the entire build or eliminating this feature
            // entirely.
            VariantWithOverloadAttributes key = new VariantWithOverloadAttributes(identifier, attributes);
            return resolvedVariantCache.computeIfAbsent(key, id -> new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifactsToResolve, new DefaultComponentArtifactResolver(component, artifactResolver)));
        }
    }

    private static ImmutableCapabilities withImplicitCapability(CapabilitiesMetadata capabilitiesMetadata, ComponentArtifactResolveMetadata component) {
        // TODO: This doesn't seem right. We should know the capability of the variant before we get here instead of assuming that it's the same as the owner
        if (capabilitiesMetadata.getCapabilities().isEmpty()) {
            return getImplicitCapability(component);
        } else {
            return ImmutableCapabilities.of(capabilitiesMetadata);
        }
    }

    private static ImmutableCapabilities getImplicitCapability(ComponentArtifactResolveMetadata component) {
        return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(component.getModuleVersionId()));
    }

    @Override
    public ArtifactSet resolveArtifacts(ComponentArtifactResolveMetadata component, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes) {
        VariantResolveMetadata.Identifier identifier = null;
        if (artifacts.size() == 1) {
            identifier = new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId());
        }

        ComponentIdentifier componentIdentifier = component.getId();
        ResolvedVariant resolvedVariant = new ArtifactBackedResolvedVariant(
            identifier,
            Describables.of(componentIdentifier),
            artifactTypeRegistry.mapAttributesFor(component.getAttributes(), artifacts),
            getImplicitCapability(component),
            ImmutableList.copyOf(artifacts),
            new DefaultComponentArtifactResolver(component, artifactResolver)
        );

        return new DefaultArtifactSet(
            componentIdentifier,
            component.getAttributesSchema(),
            overriddenAttributes,
            () -> Collections.singleton(resolvedVariant),
            Collections.singleton(resolvedVariant)
        );
    }

    private static class SingleArtifactVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final ComponentArtifactIdentifier artifactIdentifier;

        public SingleArtifactVariantIdentifier(ComponentArtifactIdentifier artifactIdentifier) {
            this.artifactIdentifier = artifactIdentifier;
        }

        @Override
        public int hashCode() {
            return artifactIdentifier.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            SingleArtifactVariantIdentifier other = (SingleArtifactVariantIdentifier) obj;
            return artifactIdentifier.equals(other.artifactIdentifier);
        }
    }
}
