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
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link LocalConfigurationMetadata} used to represent a single Configuration.
 * <p>
 * TODO: This class should be split up into a separate Metadata and State type in order to track
 * artifact resolution state separately.
 */
public final class DefaultLocalConfigurationMetadata implements LocalConfigurationMetadata, LocalConfigurationGraphResolveMetadata {

    private final String name;
    private final String description;
    private final ComponentIdentifier componentId;
    private final boolean transitive;
    private final boolean visible;
    private final ImmutableSet<String> hierarchy;
    private final ImmutableAttributes attributes;
    private final boolean canBeConsumed;
    private final boolean deprecatedForConsumption;
    private final boolean canBeResolved;
    private final ImmutableCapabilities capabilities;
    private final CalculatedValue<ConfigurationDependencyMetadata> dependencies;

    // TODO: Move all this lazy artifact stuff to a "State" type.
    private final Set<LocalVariantMetadata> variants;
    private final CalculatedValueContainerFactory factory;
    private final CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifacts;

    public DefaultLocalConfigurationMetadata(
        String name,
        String description,
        ComponentIdentifier componentId,
        boolean visible,
        boolean transitive,
        Set<String> hierarchy,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        boolean canBeConsumed,
        boolean deprecatedForConsumption,
        boolean canBeResolved,
        CalculatedValue<ConfigurationDependencyMetadata> dependencies,
        Set<LocalVariantMetadata> variants,
        CalculatedValueContainerFactory factory,
        CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> artifacts
    ) {
        this.name = name;
        this.description = description;
        this.componentId = componentId;
        this.visible = visible;
        this.transitive = transitive;
        this.hierarchy = ImmutableSet.copyOf(hierarchy);
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.canBeConsumed = canBeConsumed;
        this.deprecatedForConsumption = deprecatedForConsumption;
        this.canBeResolved = canBeResolved;
        this.dependencies = dependencies;
        this.variants = variants;
        this.factory = factory;
        this.artifacts = artifacts;
    }

    @Override
    public LocalConfigurationMetadata copyWithTransformedArtifacts(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer) {
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
                ImmutableCapabilities.of(oldVariant.getCapabilities()), newArtifacts
            ));
        }

        CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> transformedArtifacts =
            factory.create(Describables.of(description, "artifacts"), context ->
                prepareToResolveArtifacts().getArtifacts().stream()
                    .map(artifactTransformer::transform)
                    .collect(ImmutableList.toImmutableList())
            );

        return new DefaultLocalConfigurationMetadata(
            name, description, componentId, visible, transitive, hierarchy, attributes, capabilities,
            canBeConsumed, deprecatedForConsumption, canBeResolved,
            dependencies, copiedVariants.build(), factory, transformedArtifacts
        );
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(componentId, "configuration", name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ImmutableSet<String> getHierarchy() {
        return hierarchy;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isVisible() {
        return visible;
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
    public boolean isCanBeConsumed() {
        return canBeConsumed;
    }

    @Override
    public boolean isDeprecatedForConsumption() {
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
    public LocalConfigurationMetadata prepareToResolveArtifacts() {
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
    public ComponentArtifactMetadata artifact(IvyArtifactName ivyArtifactName) {
        for (ComponentArtifactMetadata candidate : getArtifacts()) {
            if (candidate.getName().equals(ivyArtifactName)) {
                return candidate;
            }
        }

        return new MissingLocalArtifactMetadata(componentId, ivyArtifactName);
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
     * The aggregated dependencies, dependency constraints, and excludes for this
     * configuration and all configurations in its hierarchy.
     */
    public static class ConfigurationDependencyMetadata {
        public final List<LocalOriginDependencyMetadata> dependencies;
        public final Set<LocalFileDependencyMetadata> files;
        public final ImmutableList<ExcludeMetadata> excludes;

        public ConfigurationDependencyMetadata(
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
