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
import org.gradle.api.artifacts.ResolvedArtifact;
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
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class DefaultArtifactSet implements ArtifactSet {
    private final ComponentIdentifier componentIdentifier;
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleSource moduleSource;
    private final ModuleExclusion exclusions;
    private final Set<? extends VariantMetadata> variants;
    private final AttributesSchemaInternal schema;
    private final ArtifactResolver artifactResolver;
    private final Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts;
    private final long id;
    private final ArtifactTypeRegistry artifactTypeRegistry;

    public DefaultArtifactSet(ComponentIdentifier componentIdentifier, ModuleVersionIdentifier ownerId, ModuleSource moduleSource, ModuleExclusion exclusions, Set<? extends VariantMetadata> variants, AttributesSchemaInternal schema,  ArtifactResolver artifactResolver, Map<ComponentArtifactIdentifier, ResolvedArtifact> allResolvedArtifacts, long id, ArtifactTypeRegistry artifactTypeRegistry) {
        this.componentIdentifier = componentIdentifier;
        this.moduleVersionIdentifier = ownerId;
        this.moduleSource = moduleSource;
        this.exclusions = exclusions;
        this.variants = variants;
        this.schema = schema;
        this.artifactResolver = artifactResolver;
        this.allResolvedArtifacts = allResolvedArtifacts;
        this.id = id;
        this.artifactTypeRegistry = artifactTypeRegistry;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        return snapshot().select(componentFilter, selector);
    }

    @Override
    public ArtifactSet snapshot() {
        ImmutableSet.Builder<ResolvedVariant> result = ImmutableSet.builder();
        for (VariantMetadata variant : variants) {
            Set<? extends ComponentArtifactMetadata> artifacts = variant.getArtifacts();
            Set<ResolvedArtifact> resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>(artifacts.size());

            // Apply any artifact type mappings to the attributes of the variant
            ImmutableAttributes attributes = artifactTypeRegistry.mapAttributesFor(variant);

            for (ComponentArtifactMetadata artifact : artifacts) {
                IvyArtifactName artifactName = artifact.getName();
                if (exclusions.excludeArtifact(moduleVersionIdentifier.getModule(), artifactName)) {
                    continue;
                }

                ResolvedArtifact resolvedArtifact = allResolvedArtifacts.get(artifact.getId());
                if (resolvedArtifact == null) {
                    Factory<File> artifactSource = new LazyArtifactSource(artifact, moduleSource, artifactResolver);
                    resolvedArtifact = new DefaultResolvedArtifact(moduleVersionIdentifier, artifactName, artifact.getId(), artifact.getBuildDependencies(), artifactSource);
                    allResolvedArtifacts.put(artifact.getId(), resolvedArtifact);
                }
                resolvedArtifacts.add(resolvedArtifact);
            }
            result.add(ArtifactBackedResolvedVariant.create(variant.asDescribable(), attributes, resolvedArtifacts));
        }
        return new ArtifactSetSnapshot(id, componentIdentifier, result.build(), schema);
    }

    private static class ArtifactSetSnapshot implements ArtifactSet, ResolvedVariantSet {
        private final long id;
        private final ComponentIdentifier componentIdentifier;
        private final Set<ResolvedVariant> variants;
        private final AttributesSchemaInternal schema;

        ArtifactSetSnapshot(long id, ComponentIdentifier componentIdentifier, Set<ResolvedVariant> variants, AttributesSchemaInternal schema) {
            this.id = id;
            this.componentIdentifier = componentIdentifier;
            this.variants = variants;
            this.schema = schema;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public ArtifactSet snapshot() {
            return this;
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
        public Set<ResolvedVariant> getVariants() {
            return variants;
        }

        @Override
        public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
            if (!componentFilter.isSatisfiedBy(componentIdentifier)) {
                return ResolvedArtifactSet.EMPTY;
            } else {
                return selector.select(this);
            }
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
