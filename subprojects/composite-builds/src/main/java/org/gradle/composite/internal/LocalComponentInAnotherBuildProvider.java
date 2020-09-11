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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentProvider;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.LocalComponentMetadata;

import java.util.concurrent.ExecutionException;

/**
 * Provides the metadata for a local component consumed from a build that is not the producing build.
 *
 * Currently, the metadata for a component is different based on whether it is consumed from the producing build or from another build. This difference should go away, but in the meantime this class provides the mapping.
 */
public class LocalComponentInAnotherBuildProvider implements LocalComponentProvider {
    private final ProjectStateRegistry projectRegistry;
    private final IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder;
    private final LoadingCache<ProjectComponentIdentifier, LocalComponentMetadata> projectMetadata = CacheBuilder.newBuilder().build(new CacheLoader<ProjectComponentIdentifier, LocalComponentMetadata>() {
        @Override
        public LocalComponentMetadata load(ProjectComponentIdentifier projectIdentifier) {
            return getRegisteredProject(projectIdentifier);
        }
    });

    public LocalComponentInAnotherBuildProvider(ProjectStateRegistry projectRegistry, IncludedBuildDependencyMetadataBuilder dependencyMetadataBuilder) {
        this.projectRegistry = projectRegistry;
        this.dependencyMetadataBuilder = dependencyMetadataBuilder;
    }

    @Override
    public LocalComponentMetadata getComponent(ProjectComponentIdentifier project) {
        try {
            return projectMetadata.get(project);
        } catch (ExecutionException | UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private LocalComponentMetadata getRegisteredProject(final ProjectComponentIdentifier projectId) {
        ProjectState projectState = projectRegistry.stateFor(projectId);
        // TODO - this should work for any build, rather than just an included build
        IncludedBuildState includedBuild = (IncludedBuildState) projectState.getOwner();
        includedBuild.getConfiguredBuild();
        // Metadata builder uses mutable project state, so synchronize access to the project state
        return projectState.fromMutableState(p -> dependencyMetadataBuilder.build(includedBuild, projectId));
    }
}
