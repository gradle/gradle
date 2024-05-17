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
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.BuildTreeLocalComponentProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.Factory;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link BuildTreeLocalComponentProvider}.
 */
public class DefaultBuildTreeLocalComponentProvider implements BuildTreeLocalComponentProvider, HoldsProjectState {

    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentGraphResolveStateFactory resolveStateFactory;
    private final LocalComponentCache localComponentCache;
    private final LocalComponentProvider localComponentProvider;

    /**
     * Caches the "true" metadata instances for local components.
     */
    private final ConcurrentMetadataCache originalComponents;

    /**
     * Contains copies of metadata instances in {@link #originalComponents}, except
     * with the component identifier replaced with the foreign counterpart.
     */
    private final ConcurrentMetadataCache foreignIdentifiedComponents;

    public DefaultBuildTreeLocalComponentProvider(
        ProjectStateRegistry projectStateRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        LocalComponentGraphResolveStateFactory resolveStateFactory,
        LocalComponentCache localComponentCache,
        LocalComponentProvider localComponentProvider
    ) {
        this.projectStateRegistry = projectStateRegistry;
        this.resolveStateFactory = resolveStateFactory;
        this.localComponentCache = localComponentCache;
        this.localComponentProvider = localComponentProvider;

        this.originalComponents = new ConcurrentMetadataCache(calculatedValueContainerFactory);
        this.foreignIdentifiedComponents = new ConcurrentMetadataCache(calculatedValueContainerFactory);
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectComponentIdentifier projectIdentifier, BuildIdentifier currentBuild) {
        boolean isLocalProject = projectIdentifier.getBuild().getBuildPath().equals(currentBuild.getBuildPath());
        if (isLocalProject) {
            return getLocalComponent(projectIdentifier, projectStateRegistry.stateFor(projectIdentifier));
        } else {
            return getLocalComponentWithForeignId(projectIdentifier);
        }
    }

    private LocalComponentGraphResolveState getLocalComponent(ProjectComponentIdentifier projectIdentifier, ProjectState projectState) {
        return originalComponents.computeIfAbsent(projectIdentifier, () -> localComponentCache.computeIfAbsent(projectState, localComponentProvider::getComponent));
    }

    private LocalComponentGraphResolveState getLocalComponentWithForeignId(ProjectComponentIdentifier projectIdentifier) {
        return foreignIdentifiedComponents.computeIfAbsent(projectIdentifier, () -> copyComponentWithForeignId(projectIdentifier));
    }

    /**
     * Copes the component identified by {@code projectIdentifier}, except with its identifier replaced with the foreign counterpart.
     *
     * <p>Eventually, in Gradle 9.0, when {@link BuildIdentifier#isCurrentBuild()} is removed, all this logic can disappear.</p>
     */
    private LocalComponentGraphResolveState copyComponentWithForeignId(ProjectComponentIdentifier projectIdentifier) {
        ProjectState projectState = projectStateRegistry.stateFor(projectIdentifier);
        CompositeBuildParticipantBuildState buildState = (CompositeBuildParticipantBuildState) projectState.getOwner();
        if (buildState instanceof IncludedBuildState) {
            // Make sure the build is configured now (not do this for the root build, as we are already configuring it right now)
            buildState.ensureProjectsConfigured();
        }

        // Get the local component, then transform it to have a foreign identifier
        // This accesses project state.
        LocalComponentMetadata metadata = projectState.fromMutableState(p -> {
            LocalComponentGraphResolveState originalComponent = getLocalComponent(projectIdentifier, projectState);
            ProjectComponentIdentifier foreignIdentifier = buildState.idToReferenceProjectFromAnotherBuild(projectIdentifier);
            return originalComponent.copy(foreignIdentifier, originalArtifact -> {
                // Currently need to resolve the file, so that the artifact can be used in both a script classpath and the main build. Instead, this should be resolved as required
                File file = originalArtifact.getFile();
                return new CompositeProjectComponentArtifactMetadata(foreignIdentifier, originalArtifact, file);
            });
        });

        return resolveStateFactory.stateFor(metadata);
    }

    @Override
    public void discardAll() {
        originalComponents.clear();
        foreignIdentifiedComponents.clear();
    }

    private static class ConcurrentMetadataCache {

        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final Map<ProjectComponentIdentifier, CalculatedValueContainer<LocalComponentGraphResolveState, ?>> cache = new ConcurrentHashMap<>();

        public ConcurrentMetadataCache(CalculatedValueContainerFactory calculatedValueContainerFactory) {
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        }

        private LocalComponentGraphResolveState computeIfAbsent(ProjectComponentIdentifier projectIdentifier, Factory<LocalComponentGraphResolveState> factory) {
            CalculatedValueContainer<LocalComponentGraphResolveState, ?> valueContainer = cache.computeIfAbsent(projectIdentifier, projectComponentIdentifier ->
                calculatedValueContainerFactory.create(Describables.of("metadata of", projectIdentifier), context -> factory.create()));
            // Calculate the value after adding the entry to the map, so that the value container can take care of thread synchronization
            valueContainer.finalizeIfNotAlready();
            return valueContainer.get();
        }

        public void clear() {
            cache.clear();
        }
    }
}
