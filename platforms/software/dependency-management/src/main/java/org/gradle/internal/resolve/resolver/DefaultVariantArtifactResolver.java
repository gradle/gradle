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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.jspecify.annotations.Nullable;

public class DefaultVariantArtifactResolver implements VariantArtifactResolver {
    private final ImmutableArtifactTypeRegistry artifactTypeRegistry;
    private final ArtifactResolver artifactResolver;
    private final ResolvedVariantCache resolvedVariantCache;

    public DefaultVariantArtifactResolver(
        ArtifactResolver artifactResolver,
        ImmutableArtifactTypeRegistry artifactTypeRegistry,
        ResolvedVariantCache resolvedVariantCache
    ) {
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

        return resolveVariantArtifactSet(component, adhoc);
    }

    @Override
    public ResolvedVariant resolveVariantArtifactSet(ComponentArtifactResolveMetadata component, VariantResolveMetadata variantArtifacts) {

        // TODO #31538: In order to apply the artifact type registry, we need to realize the artifacts now, earlier than we should.
        // Since the artifact type registry must be applied before artifact selection, which occurs before task dependencies
        // execute, and since the artifact type registry is a function of the artifacts themselves, which are only known after task
        // dependencies execute, the artifact type registry is inherently flawed. It must be deprecated and removed.
        ImmutableList<? extends ComponentArtifactMetadata> artifacts = variantArtifacts.getArtifacts();

        VariantResolveMetadata.Identifier artifactSetId = variantArtifacts.getIdentifier();
        if (artifactSetId == null || !variantArtifacts.isEligibleForCaching()) {
            return createResolvedVariant(artifactSetId, component, variantArtifacts, artifactTypeRegistry, artifacts);
        }

        // We use the artifact type registry as a key here, since for each consumer the registry may be different.
        // The registry is interned and is safe to be used as a cache key. Ideally, we would do away with the concept of the
        // artifact type registry entirely, as by design it means we need to look at the artifacts of a variant in order to perform
        // artifact selection -- a process that occurs before artifact files are even created.
        ResolvedVariantCache.CacheKey key = new ResolvedVariantCache.CacheKey(artifactSetId, artifactTypeRegistry);

        // Try first without locking
        ResolvedVariant value = resolvedVariantCache.get(key);
        if (value != null) {
            return value;
        }

        // Calculate the value with locking
        return resolvedVariantCache.computeIfAbsent(key, k ->
            createResolvedVariant(k.variantIdentifier, component, variantArtifacts, k.artifactTypeRegistry, artifacts)
        );
    }

    private ResolvedVariant createResolvedVariant(
        VariantResolveMetadata.@Nullable Identifier identifier,
        ComponentArtifactResolveMetadata component,
        VariantResolveMetadata artifactVariant,
        ImmutableArtifactTypeRegistry artifactTypeRegistry,
        ImmutableList<? extends ComponentArtifactMetadata> artifacts
    ) {

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
        return new ArtifactBackedResolvedVariant(
            identifier,
            artifactVariant.asDescribable(),
            attributes,
            capabilities,
            artifacts,
            new DefaultComponentArtifactResolver(component, artifactResolver)
        );
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
