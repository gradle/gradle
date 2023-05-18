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
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.LinkedHashSet;
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
    private final List<LocalOriginDependencyMetadata> configurationDependencies;
    private final Set<LocalFileDependencyMetadata> configurationFileDependencies;
    private final ImmutableList<ExcludeMetadata> configurationExcludes;

    // TODO: Move all this lazy artifact stuff to a "State" type.
    private final Set<LocalVariantMetadata> variants;
    private final CalculatedValueContainerFactory factory;
    private final CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts;

    /**
     * Creates a configuration metadata with lazily constructed artifact metadata.
     */
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
        List<LocalOriginDependencyMetadata> configurationDependencies,
        Set<LocalFileDependencyMetadata> configurationFileDependencies,
        List<ExcludeMetadata> configurationExcludes,
        Set<LocalVariantMetadata> variants,
        final List<PublishArtifact> definedArtifacts,
        ModelContainer<?> model,
        CalculatedValueContainerFactory factory,
        LocalComponentMetadata component
    ) {
        this(
            name, description, componentId, visible, transitive, hierarchy, attributes, capabilities, canBeConsumed, deprecatedForConsumption,
            canBeResolved, configurationDependencies, configurationFileDependencies, configurationExcludes, variants, factory,
            getLazyArtifacts(definedArtifacts, name, description, hierarchy, model, factory, component)
        );
    }

    /**
     * Creates a configuration metadata with eagerly constructed artifact metadata.
     */
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
        List<LocalOriginDependencyMetadata> configurationDependencies,
        Set<LocalFileDependencyMetadata> configurationFileDependencies,
        List<ExcludeMetadata> configurationExcludes,
        Set<LocalVariantMetadata> variants,
        CalculatedValueContainerFactory factory,
        List<LocalComponentArtifactMetadata> artifacts
    ) {
        this(
            name, description, componentId, visible, transitive, hierarchy, attributes, capabilities, canBeConsumed, deprecatedForConsumption,
            canBeResolved, configurationDependencies, configurationFileDependencies, configurationExcludes, variants, factory,
            factory.create(Describables.of(description, "artifacts"), ImmutableList.copyOf(artifacts))
        );
    }

    private DefaultLocalConfigurationMetadata(
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
        List<LocalOriginDependencyMetadata> configurationDependencies,
        Set<LocalFileDependencyMetadata> configurationFileDependencies,
        List<ExcludeMetadata> configurationExcludes,
        Set<LocalVariantMetadata> variants,
        CalculatedValueContainerFactory factory,
        CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts
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
        this.configurationDependencies = configurationDependencies;
        this.configurationFileDependencies = configurationFileDependencies;
        this.configurationExcludes = ImmutableList.copyOf(configurationExcludes);
        this.variants = variants;
        this.factory = factory;
        this.artifacts = artifacts;
    }

    /**
     * Creates a calculated value container which lazily constructs this configuration's artifacts
     * by traversing all configurations in the hierarchy and collecting their artifacts.
     */
    private static CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> getLazyArtifacts(
        List<PublishArtifact> sourceArtifacts, String name, String description, Set<String> hierarchy,
        ModelContainer<?> model, CalculatedValueContainerFactory factory, LocalComponentMetadata component
    ) {
        return factory.create(Describables.of(description, "artifacts"), context -> {
            if (sourceArtifacts.isEmpty() && hierarchy.isEmpty()) {
                return ImmutableList.of();
            } else {
                return model.fromMutableState(m -> {
                    Set<LocalComponentArtifactMetadata> result = new LinkedHashSet<>(sourceArtifacts.size());
                    for (PublishArtifact sourceArtifact : sourceArtifacts) {
                        // The following line may realize tasks, so we wrap this code in a CalculatedValue.
                        result.add(new PublishArtifactLocalArtifactMetadata(component.getId(), sourceArtifact));
                    }
                    for (String config : hierarchy) {
                        if (config.equals(name)) {
                            continue;
                        }
                        // TODO: Deprecate the behavior of inheriting artifacts from parent configurations.
                        LocalConfigurationMetadata parent = component.getConfiguration(config);
                        result.addAll(parent.prepareToResolveArtifacts().getArtifacts());
                    }
                    return ImmutableList.copyOf(result);
                });
            }
        });
    }

    @Override
    public LocalConfigurationMetadata copy(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer) {
        // TODO: This method is implemented very inefficiently. We should apply the transformer to the artifacts
        // lazily so that we don't need to prepareToResolveArtifacts here.

        ImmutableSet.Builder<LocalVariantMetadata> copiedVariants = ImmutableSet.builder();
        for (LocalVariantMetadata oldVariant : variants) {
            ImmutableList<LocalComponentArtifactMetadata> newArtifacts =
                oldVariant.prepareToResolveArtifacts().getArtifacts().stream()
                    .map(artifactTransformer::transform)
                    .collect(ImmutableList.toImmutableList());

            copiedVariants.add(new LocalVariantMetadata(
                oldVariant.getName(), oldVariant.getIdentifier(), oldVariant.asDescribable(), oldVariant.getAttributes(),
                (ImmutableCapabilities) oldVariant.getCapabilities(), newArtifacts, factory)
            );
        }

        ImmutableList<LocalComponentArtifactMetadata> copiedArtifacts =
            prepareToResolveArtifacts().getArtifacts().stream()
                .map(artifactTransformer::transform)
                .collect(ImmutableList.toImmutableList());

        return new DefaultLocalConfigurationMetadata(
            name, description, componentId, visible, transitive, hierarchy, attributes, capabilities,
            canBeConsumed, deprecatedForConsumption, canBeResolved,
            configurationDependencies, configurationFileDependencies, configurationExcludes,
            copiedVariants.build(), factory, copiedArtifacts
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
    public boolean isCanBeResolved() {
        return canBeResolved;
    }

    @Override
    public List<? extends LocalOriginDependencyMetadata> getDependencies() {
        return configurationDependencies;
    }

    @Override
    public Set<LocalFileDependencyMetadata> getFiles() {
        return configurationFileDependencies;
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return configurationExcludes;
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

}
