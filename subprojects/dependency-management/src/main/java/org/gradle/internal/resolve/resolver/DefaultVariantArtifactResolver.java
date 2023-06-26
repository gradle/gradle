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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.component.model.VariantWithOverloadAttributes;

public class DefaultVariantArtifactResolver implements VariantArtifactResolver {
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;

    private final ResolvedVariantCache resolvedVariantCache;

    public DefaultVariantArtifactResolver(ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry, ResolvedVariantCache resolvedVariantCache) {
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactResolver = artifactResolver;
        this.resolvedVariantCache = resolvedVariantCache;
    }

    @Override
    public ResolvedVariant resolveAdhocVariant(ComponentArtifactResolveMetadata component, ImmutableList<? extends ComponentArtifactMetadata> artifacts) {
        VariantResolveMetadata.Identifier identifier = artifacts.size() == 1
            ? new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId())
            : null;

        VariantResolveMetadata adhoc = new DefaultVariantMetadata(
            "adhoc",
            identifier,
            Describables.of("adhoc variant for", component.getId()),
            component.getAttributes(),
            artifacts,
            ImmutableCapabilities.EMPTY
        );

        return toResolvedVariant(component, adhoc);
    }

    @Override
    public ResolvedVariant resolveVariant(ComponentArtifactResolveMetadata component, VariantResolveMetadata variant, ExcludeSpec exclusions) {
        VariantResolveMetadata withExclusionsApplied = applyExclusions(component, variant, exclusions);
        return toResolvedVariant(component, withExclusionsApplied);
    }

    private static VariantResolveMetadata applyExclusions(ComponentArtifactResolveMetadata component, VariantResolveMetadata variant, ExcludeSpec exclusions) {
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

        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            return new DefaultVariantMetadata(
                variant.getName(), null, variant.asDescribable(),
                variant.getAttributes(), artifactsToResolveBuilder.build(), variant.getCapabilities()
            );
        }
        return variant;
    }

    private ResolvedVariant toResolvedVariant(ComponentArtifactResolveMetadata component, VariantResolveMetadata variant) {
        DisplayName displayName = variant.asDescribable();
        VariantResolveMetadata.Identifier identifier = variant.getIdentifier();
        ImmutableList<ComponentArtifactMetadata> artifacts = ImmutableList.copyOf(variant.getArtifacts());
        ImmutableCapabilities capabilities = withImplicitCapability(variant.getCapabilities(), component);
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant.getAttributes(), artifacts);

        if (identifier == null || !variant.isEligibleForCaching()) {
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifacts, new DefaultComponentArtifactResolver(component, artifactResolver));
        } else {
            // This is a bit of a hack because we allow the artifactType registry to be different in every resolution scope.
            // This means it's not safe to assume a variant resolved in one consumer can be reused in another consumer with the same key.
            // Most of the time the artifactType registry has the same effect on the variant's attributes, but this isn't guaranteed.
            // It might be better to tighten this up by either requiring a single artifactType registry for the entire build or eliminating this feature
            // entirely.
            return resolvedVariantCache.computeIfAbsent(new VariantWithOverloadAttributes(identifier, attributes), id ->
                new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifacts, new DefaultComponentArtifactResolver(component, artifactResolver))
            );
        }
    }

    private static ImmutableCapabilities withImplicitCapability(CapabilitiesMetadata capabilitiesMetadata, ComponentArtifactResolveMetadata component) {
        // TODO: This doesn't seem right. We should know the capability of the variant before we get here instead of assuming that it's the same as the owner
        if (capabilitiesMetadata.getCapabilities().isEmpty()) {
            return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(component.getModuleVersionId()));
        } else {
            return ImmutableCapabilities.of(capabilitiesMetadata);
        }
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
