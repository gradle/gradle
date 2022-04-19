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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableResolvableArtifactResult;
import org.gradle.internal.resolve.result.DefaultBuildableResolvableArtifactResult;
import org.gradle.util.internal.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ArtifactSetFactory {
    public static ArtifactSet createFromVariantMetadata(ComponentIdentifier componentIdentifier, Set<ResolvedVariant> variants, AttributesSchemaInternal schema, ImmutableAttributes selectionAttributes) {
        return new MultipleVariantArtifactSet(componentIdentifier, schema, variants, selectionAttributes);
    }

    public static ArtifactSet adHocVariant(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, Collection<? extends ComponentArtifactMetadata> artifacts, ModuleSources moduleSources, ExcludeSpec exclusions, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes variantAttributes, ImmutableAttributes selectionAttributes) {
        VariantResolveMetadata.Identifier identifier = null;
        if (artifacts.size() == 1) {
            identifier = new SingleArtifactVariantIdentifier(artifacts.iterator().next().getId());
        }
        VariantResolveMetadata variantMetadata = new DefaultVariantMetadata(null, identifier, Describables.of(componentIdentifier), variantAttributes, ImmutableList.copyOf(artifacts), ImmutableCapabilities.EMPTY);
        ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSources, exclusions, artifactResolver, artifactTypeRegistry);
        return new MultipleVariantArtifactSet(componentIdentifier, schema, Collections.singleton(resolvedVariant), selectionAttributes);
    }

    private static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactResolver artifactResolver, ImmutableAttributes variantAttributes) {
        List<? extends ComponentArtifactMetadata> artifacts = variant.getArtifacts();

        // artifactsToResolve are those not excluded by their owning module
        List<? extends ComponentArtifactMetadata> artifactsToResolve = CollectionUtils.filter(artifacts,
            artifact -> !exclusions.excludesArtifact(ownerId.getModule(), artifact.getName())
        );

        boolean hasExcludedArtifact = artifactsToResolve.size() < artifacts.size();

        VariantResolveMetadata.Identifier identifier = variant.getIdentifier();
        if (hasExcludedArtifact) {
            // An ad hoc variant, has no identifier
            identifier = null;
        }

        return ArtifactBackedResolvedVariant.create(identifier, variant.asDescribable(), variantAttributes, withImplicitCapability(variant, ownerId), supplyLazilyResolvedArtifacts(ownerId, moduleSources, artifactsToResolve, artifactResolver));
    }

    public static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactResolver artifactResolver, ArtifactTypeRegistry artifactTypeRegistry) {
        // Apply any artifact type mappings to the attributes of the variant
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant.getAttributes().asImmutable(), variant.getArtifacts());
        return toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, attributes);
    }

    private static Supplier<Collection<? extends ResolvableArtifact>> supplyLazilyResolvedArtifacts(ModuleVersionIdentifier ownerId, ModuleSources moduleSources, List<? extends ComponentArtifactMetadata> artifacts, ArtifactResolver artifactResolver) {
        return () -> {
            ImmutableSet.Builder<ResolvableArtifact> resolvedArtifacts = ImmutableSet.builder();
            for (ComponentArtifactMetadata artifact : artifacts) {
                BuildableResolvableArtifactResult result = new DefaultBuildableResolvableArtifactResult();
                artifactResolver.resolveArtifact(ownerId, artifact, moduleSources, result);

                if (!result.exists()) {
                    // An optional artifact may be not exist
                    continue;
                }
                resolvedArtifacts.add(result.getResult());
            }
            return resolvedArtifacts.build();
        };
    }

    private static CapabilitiesMetadata withImplicitCapability(VariantResolveMetadata variant, ModuleVersionIdentifier identifier) {
        CapabilitiesMetadata capabilities = variant.getCapabilities();
        if (capabilities.getCapabilities().isEmpty()) {
            return ImmutableCapabilities.of(ImmutableCapability.defaultCapabilityForComponent(identifier));
        } else {
            return ImmutableCapabilities.copyAsImmutable(capabilities.getCapabilities());
        }
    }

    private static class MultipleVariantArtifactSet extends DefaultArtifactSet {
        private final Set<ResolvedVariant> variants;

        public MultipleVariantArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, Set<ResolvedVariant> variants, ImmutableAttributes selectionAttributes) {
            super(componentIdentifier, schema, selectionAttributes);
            this.variants = variants;
        }

        @Override
        public Set<ResolvedVariant> getVariants() {
            return variants;
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
