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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains zero or more variants of a particular component.
 */
public abstract class DefaultArtifactSet implements ArtifactSet, ResolvedVariantSet {
    private final ComponentIdentifier componentIdentifier;
    private final AttributesSchemaInternal schema;

    private DefaultArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema) {
        this.componentIdentifier = componentIdentifier;
        this.schema = schema;
    }

    @Override
    public ComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    public static ArtifactSet multipleVariants(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, ModuleSource moduleSource, ModuleExclusion exclusions, Set<? extends VariantMetadata> variants, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry) {
        if (variants.size() == 1) {
            VariantMetadata variantMetadata = variants.iterator().next();
            ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSource, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
            return new SingleVariantAttributeSet(componentIdentifier, schema, resolvedVariant);
        }
        ImmutableSet.Builder<ResolvedVariant> result = ImmutableSet.builder();
        for (VariantMetadata variant : variants) {
            ResolvedVariant resolvedVariant = toResolvedVariant(variant, ownerId, moduleSource, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
            result.add(resolvedVariant);
        }
        return new MultipleVariantAttributeSet(componentIdentifier, schema, result.build());
    }

    public static ArtifactSet singleVariant(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, DisplayName displayName, Set<? extends ComponentArtifactMetadata> artifacts, ModuleSource moduleSource, ModuleExclusion exclusions, AttributesSchemaInternal schema, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry) {
        VariantMetadata variantMetadata = new DefaultVariantMetadata(displayName, ImmutableAttributes.EMPTY, artifacts);
        ResolvedVariant resolvedVariant = toResolvedVariant(variantMetadata, ownerId, moduleSource, exclusions, artifactResolver, allResolvedArtifacts, artifactTypeRegistry);
        return new SingleVariantAttributeSet(componentIdentifier, schema, resolvedVariant);
    }

    private static ResolvedVariant toResolvedVariant(VariantMetadata variant, ModuleVersionIdentifier ownerId, ModuleSource moduleSource, ModuleExclusion exclusions, ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvableArtifact> allResolvedArtifacts, ArtifactTypeRegistry artifactTypeRegistry) {
        Set<? extends ComponentArtifactMetadata> artifacts = variant.getArtifacts();
        Set<ResolvableArtifact> resolvedArtifacts = new LinkedHashSet<ResolvableArtifact>(artifacts.size());

        // Apply any artifact type mappings to the attributes of the variant
        ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant);

        for (ComponentArtifactMetadata artifact : artifacts) {
            IvyArtifactName artifactName = artifact.getName();
            if (exclusions.excludeArtifact(ownerId.getModule(), artifactName)) {
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
        return ArtifactBackedResolvedVariant.create(variant.asDescribable(), attributes, resolvedArtifacts);
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

    private static class SingleVariantAttributeSet extends DefaultArtifactSet {
        private final ResolvedVariant variant;

        public SingleVariantAttributeSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ResolvedVariant variant) {
            super(componentIdentifier, schema);
            this.variant = variant;
        }

        @Override
        public Set<ResolvedVariant> getVariants() {
            return ImmutableSet.of(variant);
        }
    }

    private static class MultipleVariantAttributeSet extends DefaultArtifactSet {
        private final Set<ResolvedVariant> variants;

        public MultipleVariantAttributeSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, Set<ResolvedVariant> variants) {
            super(componentIdentifier, schema);
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

        public File create() {
            DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifact, moduleSource, result);
            return result.getResult();
        }
    }
}
