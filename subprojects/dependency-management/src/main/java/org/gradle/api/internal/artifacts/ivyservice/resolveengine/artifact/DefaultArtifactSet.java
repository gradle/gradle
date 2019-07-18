/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contains zero or more variants of a particular component.
 */
public abstract class DefaultArtifactSet implements ArtifactSet, ResolvedVariantSet {
    private final ComponentIdentifier componentIdentifier;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributes selectionAttributes;

    private DefaultArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ImmutableAttributes selectionAttributes) {
        this.componentIdentifier = componentIdentifier;
        this.schema = schema;
        this.selectionAttributes = selectionAttributes;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public ImmutableAttributes getOverriddenAttributes() {
        return selectionAttributes;
    }

    public static ArtifactSet multipleVariants(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, ModuleSource moduleSource, ExcludeSpec exclusions, Set<? extends VariantResolveMetadata> variants, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes selectionAttributes) {
        if (variants.size() == 1) {
            VariantResolveMetadata variantMetadata = variants.iterator().next();
            ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSource, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
            return new SingleVariantArtifactSet(componentIdentifier, schema, resolvedVariant, selectionAttributes);
        }
        ImmutableSet.Builder<ResolvedVariant> result = ImmutableSet.builder();
        for (VariantResolveMetadata variant : variants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant, ownerId, moduleSource, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
            result.add(resolvedVariant);
        }
        return new MultipleVariantArtifactSet(componentIdentifier, schema, result.build(), selectionAttributes);
    }

    public static ArtifactSet singleVariant(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, DisplayName displayName, Collection<? extends ComponentArtifactMetadata> artifacts, ModuleSource moduleSource, ExcludeSpec exclusions, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry, ImmutableAttributes selectionAttributes) {
        VariantResolveMetadata variantMetadata = new DefaultVariantMetadata(displayName, ImmutableAttributes.EMPTY, ImmutableList.copyOf(artifacts), ImmutableCapabilities.EMPTY);
        ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSource, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
        return new SingleVariantArtifactSet(componentIdentifier, schema, resolvedVariant, selectionAttributes);
    }

    private static ResolvedVariant toResolvedVariant(VariantResolveMetadata variant, ModuleVersionIdentifier ownerId, ModuleSource moduleSource, ExcludeSpec exclusions, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry) {
        List<? extends ComponentArtifactMetadata> artifacts = variant.getArtifacts();
        ImmutableSet.Builder<ResolvableArtifact> resolvedArtifacts = ImmutableSet.builder();

        // Apply any artifact type mappings to the attributes of the variant
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant);

        for (ComponentArtifactMetadata artifact : artifacts) {
            IvyArtifactName artifactName = artifact.getName();
            if (exclusions.excludesArtifact(ownerId.getModule(), artifactName)) {
                continue;
            }

            ResolvableArtifact resolvedArtifact = allResolvedArtifacts.get(artifact.getId());
            if (resolvedArtifact == null) {
                Factory<File> artifactSource = new LazyArtifactSource(artifact, moduleSource, artifactResolver);
                resolvedArtifact = new DefaultResolvedArtifact(ownerId, artifactName, artifact.getId(), artifact.getBuildDependencies(), artifactSource);
                allResolvedArtifacts.put(artifact.getId(), resolvedArtifact);
            }
            resolvedArtifacts.add(resolvedArtifact);
        }
        return ArtifactBackedResolvedVariant.create(variant.asDescribable(), attributes, resolvedArtifacts.build());
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public Describable asDescribable() {
        return Describables.of(componentIdentifier);
    }

    @Override
    public AttributesSchemaInternal getSchema() {
        return schema;
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        if (!componentFilter.isSatisfiedBy(componentIdentifier)) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            return selector.select(this);
        }
    }

    private static class SingleVariantArtifactSet extends DefaultArtifactSet {
        private final ResolvedVariant variant;

        public SingleVariantArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ResolvedVariant variant, ImmutableAttributes selectionAttributes) {
            super(componentIdentifier, schema, selectionAttributes);
            this.variant = variant;
        }

        @Override
        public Set<ResolvedVariant> getVariants() {
            return ImmutableSet.of(variant);
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

    private static class LazyArtifactSource implements Factory<File> {
        private final ArtifactResolver artifactResolver;
        private final ModuleSource moduleSource;
        private final ComponentArtifactMetadata artifact;

        private LazyArtifactSource(ComponentArtifactMetadata artifact, ModuleSource moduleSource, ArtifactResolver artifactResolver) {
            this.artifact = artifact;
            this.artifactResolver = artifactResolver;
            this.moduleSource = moduleSource;
        }

        @Override
        public File create() {
            DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifact, moduleSource, result);
            return result.getResult();
        }
    }
}
