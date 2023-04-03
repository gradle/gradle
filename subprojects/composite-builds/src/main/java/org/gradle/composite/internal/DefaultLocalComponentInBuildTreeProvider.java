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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentInBuildTreeProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Describables;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.DefaultLocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides the metadata for a local component consumed from any build in the build tree.
 */
public class DefaultLocalComponentInBuildTreeProvider implements LocalComponentInBuildTreeProvider, HoldsProjectState {
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ProjectStateRegistry projectStateRegistry;

    // TODO: Ideally we should only need a single cache for all project components.
    // However, builds which are referenced from another build need to use a "foreign" identifier, which
    // means we need to cache a separate copy of each component with that identifier.
    private final Map<ProjectComponentIdentifier, CalculatedValueContainer<LocalComponentGraphResolveState, ?>> currentBuildProjects = new ConcurrentHashMap<>();
    private final Map<ProjectComponentIdentifier, CalculatedValueContainer<LocalComponentGraphResolveState, ?>> otherBuildProjects = new ConcurrentHashMap<>();

    public DefaultLocalComponentInBuildTreeProvider(CalculatedValueContainerFactory calculatedValueContainerFactory, ProjectStateRegistry projectStateRegistry) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.projectStateRegistry = projectStateRegistry;
    }

    public LocalComponentGraphResolveState getComponent(BuildIdentifier currentBuild, ProjectComponentIdentifier projectIdentifier) {
        ProjectState projectState = projectStateRegistry.stateFor(projectIdentifier);
        LocalComponentGraphResolveState component = getLocallyIdentifiedComponent(projectState);

        if (projectIdentifier.getBuild().equals(currentBuild)) {
            return component;
        } else {
            return getForeignIdentifiedComponent(component, projectState);
        }
    }

    private LocalComponentGraphResolveState getLocallyIdentifiedComponent(ProjectState projectState) {
        ProjectComponentIdentifier projectIdentifier = projectState.getComponentIdentifier();

        CalculatedValueContainer<LocalComponentGraphResolveState, ?> valueContainer = currentBuildProjects.computeIfAbsent(projectIdentifier, projectComponentIdentifier ->
            calculatedValueContainerFactory.create(Describables.of("metadata of", projectIdentifier), new LocallyIdentifiedMetadataSupplier(projectState)));

        // Calculate the value after adding the entry to the map, so that the value container can take care of thread synchronization
        valueContainer.finalizeIfNotAlready();
        return valueContainer.get();
    }

    public LocalComponentGraphResolveState getForeignIdentifiedComponent(LocalComponentGraphResolveState component, ProjectState projectState) {
        ProjectComponentIdentifier projectIdentifier = projectState.getComponentIdentifier();

        CalculatedValueContainer<LocalComponentGraphResolveState, ?> valueContainer = otherBuildProjects.computeIfAbsent(projectIdentifier, projectComponentIdentifier ->
            calculatedValueContainerFactory.create(Describables.of("metadata of", projectIdentifier), new ForeignIdentifiedMetadataSupplier(component, projectState)));

        // Calculate the value after adding the entry to the map, so that the value container can take care of thread synchronization
        valueContainer.finalizeIfNotAlready();
        return valueContainer.get();
    }

    @Override
    public void discardAll() {
        currentBuildProjects.clear();
        otherBuildProjects.clear();
    }

    private static class LocallyIdentifiedMetadataSupplier implements ValueCalculator<LocalComponentGraphResolveState> {
        private final ProjectState projectState;

        public LocallyIdentifiedMetadataSupplier(ProjectState projectState) {
            this.projectState = projectState;
        }

        @Override
        public LocalComponentGraphResolveState calculateValue(NodeExecutionContext context) {
            BuildState buildState = projectState.getOwner();
            if (buildState instanceof IncludedBuildState) {
                // make sure the build is configured now (not do this for the root build, as we are already configuring it right now)
                buildState.ensureProjectsConfigured();
            }

            // Metadata builder uses mutable project state, so synchronize access to the project state
            return projectState.fromMutableState(p -> {
                LocalComponentProvider provider = p.getGradle().getServices().get(LocalComponentProvider.class);
                return provider.getComponent(projectState);
            });
        }
    }

    private static class ForeignIdentifiedMetadataSupplier implements ValueCalculator<LocalComponentGraphResolveState> {

        private final LocalComponentGraphResolveState component;
        private final ProjectState projectState;

        public ForeignIdentifiedMetadataSupplier(LocalComponentGraphResolveState component, ProjectState projectState) {
            this.component = component;
            this.projectState = projectState;
        }

        @Override
        public LocalComponentGraphResolveState calculateValue(NodeExecutionContext context) {
            ProjectComponentIdentifier projectIdentifier = projectState.getComponentIdentifier();
            CompositeBuildParticipantBuildState buildState = (CompositeBuildParticipantBuildState) projectState.getOwner();
            ProjectComponentIdentifier foreignIdentifier = buildState.idToReferenceProjectFromAnotherBuild(projectIdentifier);

            LocalComponentMetadata metadata = component.copy(foreignIdentifier, originalArtifact -> {
                // Currently need to resolve the file, so that the artifact can be used in both
                // a script classpath and the main build. Instead, this should be resolved as required.
                File file = projectState.fromMutableState(p -> originalArtifact.getFile());
                return new CompositeProjectComponentArtifactMetadata(foreignIdentifier, originalArtifact, file);
            });

            return new DefaultLocalComponentGraphResolveState(metadata);
        }
    }
}
