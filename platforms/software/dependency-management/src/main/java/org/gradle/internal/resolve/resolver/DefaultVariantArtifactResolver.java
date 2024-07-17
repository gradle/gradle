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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
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

import javax.annotation.Nullable;

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

        return toResolvedVariant(component, adhoc, identifier, artifacts);
    }

    @Override
    public ResolvedVariant resolveVariant(ComponentArtifactResolveMetadata component, VariantResolveMetadata artifactVariant) {
        return toResolvedVariant(component, artifactVariant, artifactVariant.getIdentifier(), artifactVariant.getArtifacts());
    }

    @Override
    public ResolvedVariant resolveVariant(ComponentArtifactResolveMetadata component, VariantResolveMetadata artifactVariant, ExcludeSpec exclusions) {
        ImmutableList<? extends ComponentArtifactMetadata> sourceArtifacts = artifactVariant.getArtifacts();
        ImmutableList<ComponentArtifactMetadata> overrideArtifacts = maybeExcludeArtifacts(component, sourceArtifacts, exclusions);
        if (overrideArtifacts != null) {
            // If we override artifacts, this is an adhoc variant, therefore it has no identifier.
            return toResolvedVariant(component, artifactVariant, null, overrideArtifacts);
        } else {
            return toResolvedVariant(component, artifactVariant, artifactVariant.getIdentifier(), sourceArtifacts);
        }
    }

    @Nullable
    private static ImmutableList<ComponentArtifactMetadata> maybeExcludeArtifacts(ComponentArtifactResolveMetadata component, ImmutableList<? extends ComponentArtifactMetadata> artifacts, ExcludeSpec exclusions) {
        ModuleIdentifier module = component.getModuleVersionId().getModule();

        // artifactsToResolve are those not excluded by their owning module
        boolean hasExcludedArtifact = false;
        ImmutableList.Builder<ComponentArtifactMetadata> artifactsToResolveBuilder = ImmutableList.builderWithExpectedSize(artifacts.size());
        for (ComponentArtifactMetadata artifact : artifacts) {
            if (!exclusions.excludesArtifact(module, artifact.getName())) {
                artifactsToResolveBuilder.add(artifact);
            } else {
                hasExcludedArtifact = true;
            }
        }

        if (hasExcludedArtifact) {
            return artifactsToResolveBuilder.build();
        }

        return null;
    }

    private ResolvedVariant toResolvedVariant(
        ComponentArtifactResolveMetadata component,
        VariantResolveMetadata artifactVariant,
        @Nullable VariantResolveMetadata.Identifier identifier,
        ImmutableList<? extends ComponentArtifactMetadata> artifacts
    ) {
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(artifactVariant.getAttributes(), artifacts);

        if (identifier == null || !artifactVariant.isEligibleForCaching()) {
            DisplayName displayName = artifactVariant.asDescribable();
            ImmutableCapabilities capabilities = withImplicitCapability(artifactVariant.getCapabilities(), component);
            return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifacts, new DefaultComponentArtifactResolver(component, artifactResolver));
        } else {
            // This is a bit of a hack because we allow the artifactType registry to be different in every resolution scope.
            // This means it's not safe to assume a variant resolved in one consumer can be reused in another consumer with the same key.
            // Most of the time the artifactType registry has the same effect on the variant's attributes, but this isn't guaranteed.
            // It might be better to tighten this up by either requiring a single artifactType registry for the entire build or eliminating this feature
            // entirely.
            return resolvedVariantCache.computeIfAbsent(new VariantWithOverloadAttributes(identifier, attributes), id -> {
                DisplayName displayName = artifactVariant.asDescribable();
                ImmutableCapabilities capabilities = withImplicitCapability(artifactVariant.getCapabilities(), component);
                return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifacts, new DefaultComponentArtifactResolver(component, artifactResolver));
            });
        }
    }

    private static ImmutableCapabilities withImplicitCapability(ImmutableCapabilities capabilities, ComponentArtifactResolveMetadata component) {
        // TODO: This doesn't seem right. We should know the capability of the variant before we get here instead of assuming that it's the same as the owner
        if (capabilities.asSet().isEmpty()) {
            return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(component.getModuleVersionId()));
        } else {
            return capabilities;
        }
    }

    /**
     * Identifier for adhoc variants with a single artifact
     */
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

    /**
     * A cache key for the resolved variant cache that includes the attributes of the variant.
     * The attributes are necessary here, as the artifact type registry in each consuming
     * project may be different, resulting in a different computed attributes set for any
     * given producer variant.
     */
    public static class VariantWithOverloadAttributes implements ResolvedVariantCache.CacheKey {
        private final VariantResolveMetadata.Identifier variantIdentifier;
        private final ImmutableAttributes targetVariant;
        private final int hashCode;

        public VariantWithOverloadAttributes(VariantResolveMetadata.Identifier variantIdentifier, ImmutableAttributes targetVariant) {
            this.variantIdentifier = variantIdentifier;
            this.targetVariant = targetVariant;
            this.hashCode = 31 * variantIdentifier.hashCode() + targetVariant.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            VariantWithOverloadAttributes other = (VariantWithOverloadAttributes) obj;
            return variantIdentifier.equals(other.variantIdentifier) && targetVariant.equals(other.targetVariant);
        }
    }
}
