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
import org.gradle.internal.build.CompositeBuildParticipantBuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.model.CalculatedValueCache;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.io.File;

/**
 * Default implementation of {@link BuildTreeLocalComponentProvider}.
 *
 * <p>Currently, the metadata for a component is different based on whether it is consumed from the
 * producing build or from another build. This distinction can go away in Gradle 9.0.</p>
 */
public class DefaultBuildTreeLocalComponentProvider implements BuildTreeLocalComponentProvider, HoldsProjectState {

    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentCache localComponentCache;
    private final LocalComponentProvider localComponentProvider;

    /**
     * Caches the "true" metadata instances for local components.
     */
    private final CalculatedValueCache<ProjectComponentIdentifier, LocalComponentGraphResolveState> originalComponents;

    /**
     * Contains copies of metadata instances in {@link #originalComponents}, except
     * with the component identifier replaced with the foreign counterpart.
     */
    private final CalculatedValueCache<ProjectComponentIdentifier, LocalComponentGraphResolveState> foreignIdentifiedComponents;

    @Inject
    public DefaultBuildTreeLocalComponentProvider(
        ProjectStateRegistry projectStateRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        LocalComponentCache localComponentCache,
        LocalComponentProvider localComponentProvider
    ) {
        this.projectStateRegistry = projectStateRegistry;
        this.localComponentCache = localComponentCache;
        this.localComponentProvider = localComponentProvider;

        this.originalComponents = calculatedValueContainerFactory.createCache(Describables.of("local metadata"));
        this.foreignIdentifiedComponents = calculatedValueContainerFactory.createCache(Describables.of("foreign metadata"));
    }

    @Override
    public LocalComponentGraphResolveState getComponent(ProjectComponentIdentifier projectIdentifier, Path currentBuildPath) {
        boolean isLocalProject = projectIdentifier.getBuild().getBuildPath().equals(currentBuildPath.getPath());
        if (isLocalProject) {
            return getLocalComponent(projectIdentifier, projectStateRegistry.stateFor(projectIdentifier));
        } else {
            return getLocalComponentWithForeignId(projectIdentifier);
        }
    }

    private LocalComponentGraphResolveState getLocalComponent(ProjectComponentIdentifier projectIdentifier, ProjectState projectState) {
        return originalComponents.computeIfAbsent(projectIdentifier, id -> localComponentCache.computeIfAbsent(projectState, localComponentProvider::getComponent));
    }

    private LocalComponentGraphResolveState getLocalComponentWithForeignId(ProjectComponentIdentifier projectIdentifier) {
        return foreignIdentifiedComponents.computeIfAbsent(projectIdentifier, this::copyComponentWithForeignId);
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
        LocalComponentGraphResolveState originalComponent = getLocalComponent(projectIdentifier, projectState);
        ProjectComponentIdentifier foreignIdentifier = buildState.idToReferenceProjectFromAnotherBuild(projectIdentifier);
        return originalComponent.copy(foreignIdentifier, originalArtifact -> {
            // Currently need to resolve the file, so that the artifact can be used in both a script classpath and
            // the main build. This accesses project state. Instead, the file should be resolved as required.
            File file = projectState.fromMutableState(p -> originalArtifact.getFile());
            return new CompositeProjectComponentArtifactMetadata(foreignIdentifier, originalArtifact, file);
        });
    }

    @Override
    public void discardAll() {
        originalComponents.clear();
        foreignIdentifiedComponents.clear();
    }
}
