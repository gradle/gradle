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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
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
    private final ImmutableArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;
    private final ResolvedVariantCache resolvedVariantCache;

    public DefaultVariantArtifactResolver(ArtifactResolver artifactResolver, ImmutableArtifactTypeRegistry artifactTypeRegistry, ResolvedVariantCache resolvedVariantCache) {
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
        if (identifier == null || !artifactVariant.isEligibleForCaching()) {
            return createResolvedVariant(identifier, component, artifactVariant, artifacts, artifactTypeRegistry);
        }

        // We use the artifact type registry as a key here, since for each consumer the registry may be different.
        // The registry is interned and is safe to be used as a cache key. Ideally, we would do away with the concept of the
        // artifact type registry entirely, as by design it means we need to look at the artifacts of a variant in order to perform
        // artifact selection -- a process that occurs before artifact files are even created.
        ResolvedVariantCache.CacheKey key = new ResolvedVariantCache.CacheKey(identifier, artifactTypeRegistry);

        // Try first without locking
        ResolvedVariant value = resolvedVariantCache.get(key);
        if (value != null) {
            return value;
        }

        // Calculate the value with locking
        return resolvedVariantCache.computeIfAbsent(key, k ->
            createResolvedVariant(k.variantIdentifier, component, artifactVariant, artifacts, k.artifactTypeRegistry)
        );
    }

    private ResolvedVariant createResolvedVariant(
        @Nullable VariantResolveMetadata.Identifier identifier,
        ComponentArtifactResolveMetadata component,
        VariantResolveMetadata artifactVariant,
        ImmutableList<? extends ComponentArtifactMetadata> artifacts,
        ImmutableArtifactTypeRegistry artifactTypeRegistry
    ) {
        DisplayName displayName = artifactVariant.asDescribable();
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(artifactVariant.getAttributes(), artifacts);
        ImmutableCapabilities capabilities = withImplicitCapability(artifactVariant.getCapabilities(), component);

        // TODO: This value gets cached in a build-tree-scoped cache. It captures a project-scoped `artifactResolver`, which
        // is bound to the repositories of the consumer. That means subsequent resolutions of this artifact from a different
        // project will use the same resolver as the first resolution -- leading us to use repositories from another project
        // when resolving artifacts in this project. We disallow caching of external artifacts above with `isEligibleForCaching`,
        // but if we enable caching for external artifacts, we need to make sure that the `artifactResolver` is included as a
        // cache key or is provided later on during artifact resolution.

        // Better yet, the state required to resolve a variant (the `artifactResolver`) should be captured in the original
        // component artifact metadata, meaning the variant identity is tied to where it is resolved from. This way, we do
        // not need to track the resolver separately, and we can ensure the resolver that resolved a component's metadata is
        // the same one that resolves its artifacts. This would benefit greatly from "repository deduplication", where we could
        // consider repositories from multiple projects as equivalent as long as they are configured the same (same url, cache policy,
        // component metadata rules, metadata sources, etc.). We should probably leverage ComponentArtifactResolveMetadata#getSources() for this.
        return new ArtifactBackedResolvedVariant(identifier, displayName, attributes, capabilities, artifacts, new DefaultComponentArtifactResolver(component, artifactResolver));
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
}
