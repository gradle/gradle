/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.component.model.VariantWithOverloadAttributes;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServiceScope(Scopes.Build.class)
public class ProjectArtifactSetResolver {
    private final ArtifactResolver artifactResolver;
    // Move this state closer to the project metadata
    private final Map<ComponentArtifactIdentifier, ResolvableArtifact> allProjectArtifacts = new ConcurrentHashMap<>();
    private final Map<VariantResolveMetadata.Identifier, ResolvedVariant> allProjectVariants = new ConcurrentHashMap<>();

    public ProjectArtifactSetResolver(ProjectArtifactResolver artifactResolver) {
        this.artifactResolver = artifactResolver;
    }

    /**
     * Creates an {@link ArtifactSet} that represents the available artifacts for the given set of project variants.
     */
    public ArtifactSet resolveArtifacts(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, Set<? extends VariantResolveMetadata> variants, AttributesSchemaInternal schema, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes selectionAttributes) {
        ImmutableSet.Builder<ResolvedVariant> result = ImmutableSet.builderWithExpectedSize(variants.size());
        for (VariantResolveMetadata variant : variants) {
            ResolvedVariant resolvedVariant = mapVariant(ownerId, moduleSources, exclusions, artifactTypeRegistry, variant);
            result.add(resolvedVariant);
        }
        return DefaultArtifactSet.createFromVariants(componentIdentifier, result.build(), schema, selectionAttributes);
    }

    private ResolvedVariant mapVariant(ModuleVersionIdentifier ownerId, ModuleSources moduleSources, ExcludeSpec exclusions, ArtifactTypeRegistry artifactTypeRegistry, VariantResolveMetadata variant) {
        VariantResolveMetadata.Identifier identifier = variant.getIdentifier();
        if (identifier == null) {
            throw new IllegalArgumentException(String.format("Project variant %s does not have an identifier.", variant.asDescribable()));
        }

        // Apply any artifact type mappings to the attributes of the variant
        ImmutableAttributes variantAttributes = artifactTypeRegistry.mapAttributesFor(variant.getAttributes().asImmutable(), variant.getArtifacts());

        if (exclusions.mayExcludeArtifacts()) {
            // Some artifact may be excluded, so do not reuse. It might be better to apply the exclusions and reuse if none of them apply
            return DefaultArtifactSet.toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allProjectArtifacts, variantAttributes);
        }

        VariantWithOverloadAttributes key = new VariantWithOverloadAttributes(identifier, variantAttributes);
        return allProjectVariants.computeIfAbsent(key, k -> DefaultArtifactSet.toResolvedVariant(variant, ownerId, moduleSources, exclusions, artifactResolver, allProjectArtifacts, variantAttributes));
    }
}
