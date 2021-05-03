/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides the metadata for a local component consumed from a build that is not the producing build.
 *
 * Currently, the metadata for a component is different based on whether it is consumed from the producing build or from another build. This difference should go away, but in the meantime this class provides the mapping.
 */
public class LocalComponentInAnotherBuildProvider implements LocalComponentProvider {
    private final ProjectStateRegistry projectRegistry;
    private final IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final Map<ProjectComponentIdentifier, CalculatedValueContainer<LocalComponentMetadata, ?>> projectMetadata = new ConcurrentHashMap<>();

    public LocalComponentInAnotherBuildProvider(ProjectStateRegistry projectRegistry, IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.projectRegistry = projectRegistry;
        this.dependencyMetadataBuilder = dependencyMetadataBuilder;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier projectId) {
        CalculatedValueContainer<LocalComponentMetadata, ?> valueContainer = projectMetadata.computeIfAbsent(projectId, projectComponentIdentifier -> {
            ProjectState projectState = projectRegistry.stateFor(projectId);
            return calculatedValueContainerFactory.create(Describables.of("metadata for", projectId), new MetadataSupplier(projectState));
        });
        // Calculate the value after adding the entry to the map, so that the value container can take care of thread synchronization
        valueContainer.finalizeIfNotAlready();
        return valueContainer.get();
    }

    private class MetadataSupplier implements ValueCalculator<LocalComponentMetadata> {
        private final ProjectState projectState;

        public MetadataSupplier(ProjectState projectState) {
            this.projectState = projectState;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        public boolean usesMutableProjectState() {
            return true;
        }

        @Override
        public ProjectInternal getOwningProject() {
            return projectState.getMutableModel();
        }

        @Override
        public LocalComponentMetadata calculateValue(NodeExecutionContext context) {
            // TODO - this should work for any build, rather than just an included build
            CompositeBuildParticipantBuildState buildState = (CompositeBuildParticipantBuildState) projectState.getOwner();
            if (buildState instanceof IncludedBuildState) {
                // make sure the build is configured now (not do this for the root build, as we are already configuring it right now)
                ((IncludedBuildState) buildState).getConfiguredBuild();
            }
            // Metadata builder uses mutable project state, so synchronize access to the project state
            return projectState.fromMutableState(p -> dependencyMetadataBuilder.build(buildState, projectState.getComponentIdentifier()));
        }
    }
}
