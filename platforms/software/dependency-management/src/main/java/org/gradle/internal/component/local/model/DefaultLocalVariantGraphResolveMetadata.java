/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link LocalVariantGraphResolveMetadata} used to represent a single local variant.
 */
public final class DefaultLocalVariantGraphResolveMetadata implements LocalVariantGraphResolveMetadata, LocalVariantArtifactGraphResolveMetadata {

    private final String name;
    private final String description;
    private final ComponentIdentifier componentId;
    private final boolean transitive;
    private final ImmutableAttributes attributes;
    private final boolean deprecatedForConsumption;
    private final ImmutableCapabilities capabilities;

    // TODO: All this lazy state should be moved to DefaultLocalVariantGraphResolveState
    private final CalculatedValue<VariantDependencyMetadata> dependencies;
    private final Set<LocalVariantMetadata> variants;
    private final CalculatedValueContainerFactory factory;
    private final CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifacts;

    public DefaultLocalVariantGraphResolveMetadata(
        String name,
        String description,
        ComponentIdentifier componentId,
        boolean transitive,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        boolean deprecatedForConsumption,
        CalculatedValue<VariantDependencyMetadata> dependencies,
        Set<LocalVariantMetadata> variants,
        CalculatedValueContainerFactory factory,
        CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifacts
    ) {
        this.name = name;
        this.description = description;
        this.componentId = componentId;
        this.transitive = transitive;
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.deprecatedForConsumption = deprecatedForConsumption;
        this.dependencies = dependencies;
        this.variants = variants;
        this.factory = factory;
        this.artifacts = artifacts;
    }

    @Override
    public LocalVariantGraphResolveMetadata copyWithTransformedArtifacts(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer) {
        ImmutableSet.Builder<LocalVariantMetadata> copiedVariants = ImmutableSet.builder();
        for (LocalVariantMetadata oldVariant : variants) {
            CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> newArtifacts =
                factory.create(Describables.of(oldVariant.asDescribable(), "artifacts"), context ->
                    oldVariant.prepareToResolveArtifacts().getArtifacts().stream()
                        .map(artifactTransformer::transform)
                        .collect(ImmutableList.toImmutableList())
                );

            copiedVariants.add(new LocalVariantMetadata(
                oldVariant.getName(), oldVariant.getIdentifier(), oldVariant.asDescribable(), oldVariant.getAttributes(),
                oldVariant.getCapabilities(), newArtifacts
            ));
        }

        CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> transformedArtifacts =
            factory.create(Describables.of(description, "artifacts"), context ->
                prepareToResolveArtifacts().getArtifacts().stream()
                    .map(artifactTransformer::transform)
                    .collect(ImmutableList.toImmutableList())
            );

        return new DefaultLocalVariantGraphResolveMetadata(
            name, description, componentId, transitive, attributes, capabilities,
            deprecatedForConsumption,
            dependencies, copiedVariants.build(), factory, transformedArtifacts
        );
    }

    @Override
    public String toString() {
        return Describables.of(componentId, "variant", name).getDisplayName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigurationName() {
        return name;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public Set<LocalVariantMetadata> getVariants() {
        return variants;
    }

    @Override
    public boolean isDeprecated() {
        return deprecatedForConsumption;
    }

    @Override
    public List<? extends LocalOriginDependencyMetadata> getDependencies() {
        dependencies.finalizeIfNotAlready();
        return dependencies.get().dependencies;
    }

    @Override
    public Set<LocalFileDependencyMetadata> getFiles() {
        dependencies.finalizeIfNotAlready();
        return dependencies.get().files;
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        dependencies.finalizeIfNotAlready();
        return dependencies.get().excludes;
    }

    @Override
    public LocalVariantArtifactGraphResolveMetadata prepareToResolveArtifacts() {
        artifacts.finalizeIfNotAlready();
        for (LocalVariantMetadata variant : variants) {
            variant.prepareToResolveArtifacts();
        }
        return this;
    }

    @Override
    public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
        return artifacts.get();
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean isExternalVariant() {
        return false;
    }

    /**
     * The dependencies, dependency constraints, and excludes for this variant.
     */
    public static class VariantDependencyMetadata {
        public final List<LocalOriginDependencyMetadata> dependencies;
        public final Set<LocalFileDependencyMetadata> files;
        public final ImmutableList<ExcludeMetadata> excludes;

        public VariantDependencyMetadata(
            List<LocalOriginDependencyMetadata> dependencies,
            Set<LocalFileDependencyMetadata> files,
            List<ExcludeMetadata> excludes
        ) {
            this.dependencies = dependencies;
            this.files = files;
            this.excludes = ImmutableList.copyOf(excludes);
        }
    }

}
