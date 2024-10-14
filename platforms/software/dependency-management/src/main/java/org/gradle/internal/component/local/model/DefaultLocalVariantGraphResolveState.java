/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link LocalVariantGraphResolveState}.
 */
public class DefaultLocalVariantGraphResolveState implements LocalVariantGraphResolveState {

    // Metadata
    private final long instanceId;
    private final LocalVariantGraphResolveMetadata metadata;

    // State
    private final CalculatedValue<VariantDependencyMetadata> dependencies;
    private final DefaultLocalVariantArtifactResolveState artifactState;

    // Services
    private final ComponentIdGenerator idGenerator;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public DefaultLocalVariantGraphResolveState(
        long instanceId,
        ComponentIdentifier componentId,
        LocalVariantGraphResolveMetadata metadata,
        ComponentIdGenerator idGenerator,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        CalculatedValue<VariantDependencyMetadata> dependencies,
        Set<LocalVariantMetadata> artifactSets
    ) {
        this.instanceId = instanceId;
        this.metadata = metadata;

        this.dependencies = dependencies;
        this.artifactState = new DefaultLocalVariantArtifactResolveState(componentId, artifactSets);

        this.idGenerator = idGenerator;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public LocalVariantGraphResolveState copyWithTransformedArtifacts(Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer) {

        Set<LocalVariantMetadata> artifactSets = artifactState.getArtifactVariants();
        ImmutableSet.Builder<LocalVariantMetadata> copiedArtifactSets = ImmutableSet.builderWithExpectedSize(artifactSets.size());

        for (LocalVariantMetadata oldArtifactSet : artifactSets) {
            CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> newArtifacts =
                calculatedValueContainerFactory.create(Describables.of(oldArtifactSet.asDescribable(), "artifacts"), c ->
                    oldArtifactSet.getArtifacts().stream()
                        .map(artifactTransformer::transform)
                        .collect(ImmutableList.toImmutableList())
                );

            copiedArtifactSets.add(new LocalVariantMetadata(
                oldArtifactSet.getName(),
                oldArtifactSet.getIdentifier(),
                oldArtifactSet.asDescribable(),
                oldArtifactSet.getAttributes(),
                oldArtifactSet.getCapabilities(),
                newArtifacts
            ));
        }

        return new DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            artifactState.componentId,
            metadata,
            idGenerator,
            calculatedValueContainerFactory,
            dependencies,
            copiedArtifactSets.build()
        );
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public String getName() {
        return metadata.getName();
    }

    @Override
    public String toString() {
        return metadata.toString();
    }

    @Override
    public LocalVariantGraphResolveMetadata getMetadata() {
        return metadata;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return metadata.getAttributes();
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return metadata.getCapabilities();
    }

    @Override
    public Set<LocalFileDependencyMetadata> getFiles() {
        dependencies.finalizeIfNotAlready();
        return dependencies.get().files;
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        dependencies.finalizeIfNotAlready();
        return dependencies.get().dependencies;
    }

    @Override
    public List<? extends ExcludeMetadata> getExcludes() {
        dependencies.finalizeIfNotAlready();
        return dependencies.get().excludes;
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution() {
        return artifactState;
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

    private static class DefaultLocalVariantArtifactResolveState implements VariantArtifactResolveState {

        private final ComponentIdentifier componentId;
        private final Set<LocalVariantMetadata> artifactSets;

        public DefaultLocalVariantArtifactResolveState(
            ComponentIdentifier componentId,
            Set<LocalVariantMetadata> artifactSets
        ) {
            this.componentId = componentId;
            this.artifactSets = artifactSets;
        }

        @Override
        public ImmutableList<ComponentArtifactMetadata> getAdhocArtifacts(List<IvyArtifactName> dependencyArtifacts) {
            ImmutableList.Builder<ComponentArtifactMetadata> artifacts = ImmutableList.builderWithExpectedSize(dependencyArtifacts.size());
            for (IvyArtifactName dependencyArtifact : dependencyArtifacts) {
                artifacts.add(getArtifactWithName(dependencyArtifact));
            }
            return artifacts.build();
        }

        private ComponentArtifactMetadata getArtifactWithName(IvyArtifactName ivyArtifactName) {
            for (VariantResolveMetadata artifactSet : getArtifactVariants()) {
                for (ComponentArtifactMetadata candidate : artifactSet.getArtifacts()) {
                    if (candidate.getName().equals(ivyArtifactName)) {
                        return candidate;
                    }
                }
            }

            return new MissingLocalArtifactMetadata(componentId, ivyArtifactName);
        }

        @Override
        public Set<LocalVariantMetadata> getArtifactVariants() {
            return artifactSets;
        }
    }
}
