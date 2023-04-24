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

package org.gradle.internal.build;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.DisplayName;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Function;

/**
 * Encapsulates the identity and state of a particular build in a build tree.
 *
 * Implementations are not yet entirely thread-safe, but should be.
 */
public interface BuildState {
    DisplayName getDisplayName();

    /**
     * Returns the identifier for this build. The identifier is fixed for the lifetime of the build.
     */
    BuildIdentifier getBuildIdentifier();

    /**
     * Returns an identifying path for this build in the build tree. This path is fixed for the lifetime of the build.
     */
    Path getIdentityPath();

    /**
     * Is this an implicit build? An implicit build is one that is managed by Gradle and which is not addressable by user build logic.
     */
    boolean isImplicitBuild();

    /**
     * Should this build be imported into an IDE? Some implicit builds, such as source dependency builds, are not intended to be imported into the IDE or editable by users.
     */
    boolean isImportableBuild();

    /**
     * Calculates the identity path for a project in this build.
     */
    Path calculateIdentityPathForProject(Path projectPath) throws IllegalStateException;

    /**
     * Loads the projects for this build so that {@link #getProjects()} can be used, if not already done.
     * This may include running the settings script for the build, or loading this information from cache.
     */
    void ensureProjectsLoaded();

    /**
     * Have the projects been loaded, ie has {@link #ensureProjectsLoaded()} already completed for this build?
     */
    boolean isProjectsLoaded();

    /**
     * Ensures all projects in this build are configured, if not already done.
     */
    void ensureProjectsConfigured();

    /**
     * Returns the projects of this build. Fails if the projects are not yet loaded for this build.
     */
    BuildProjectRegistry getProjects();

    /**
     * Asserts that the given build can be included by this build.
     */
    void assertCanAdd(IncludedBuildSpec includedBuildSpec);

    /**
     * The root directory of the build.
     */
    File getBuildRootDir();

    /**
     * Returns the current state of the mutable model of this build. Try to avoid using the model directly.
     */
    GradleInternal getMutableModel();

    /**
     * Returns the work graph for this build.
     */
    BuildWorkGraphController getWorkGraph();

    /**
     * Runs an action against the tooling model creators of this build. May configure the build as required.
     */
    <T> T withToolingModels(Function<? super BuildToolingModelController, T> action);

    /**
     * Runs whatever work is required prior to discarding the model for this build. Called prior to {@link #resetModel()}.
     */
    ExecutionResult<Void> beforeModelReset();

    /**
     * Restarts the lifecycle of the model of this build, discarding all current model state.
     */
    void resetModel();

    /**
     * Runs whatever work is required prior to discarding the model for this build. Called at the end of the build.
     */
    ExecutionResult<Void> beforeModelDiscarded(boolean failed);
}
