/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.artifacts;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.HasInternalProtocol;

/**
 * A set of {@link Dependency} objects which can be resolved to a set of files. There are various methods on this type that you can use to get the result in different forms:
 *
 * <ul>
 *     <li>{@link #getFiles()} returns a {@link FileCollection} that provides the result as a set of {@link java.io.File} instances.</li>
 *     <li>{@link #getResolutionResult()} returns a {@link ResolutionResult} that provides information about the dependency graph.</li>
 *     <li>{@link #getArtifacts()} returns an {@link ArtifactCollection} that provides the files with additional metadata.</li>
 * </ul>
 *
 * <p>The dependencies are resolved once only, when the result is first requested. The result is reused and returned for subsequent calls. Once resolved, any mutation to the dependencies will result in an error.</p>
 */
@HasInternalProtocol
public interface ResolvableDependencies extends ArtifactView {
    /**
     * Returns the name of this set.
     *
     * @return The name. Never null.
     */
    String getName();

    /**
     * Returns the path of this set. This is a unique identifier for this set.
     *
     * @return The path. Never null.
     */
    String getPath();

    /**
     * Returns a {@link FileCollection} which contains the resolved set of files. The returned value is lazy, so dependency resolution is not performed until the contents of the collection are queried.
     *
     * <p>The {@link FileCollection} carries the task dependencies required to build the files in the result, and when used as a task input the files will be built before the task executes.</p>
     *
     * @return The collection. Never null.
     */
    @Override
    FileCollection getFiles();

    /**
     * Returns the set of dependencies which will be resolved.
     *
     * @return the dependencies. Never null.
     */
    DependencySet getDependencies();

    /**
     * Returns the set of dependency constraints which will be considered during resolution.
     *
     * @return the dependency constraints. Never null.
     *
     * @since 4.6
     */
    @Incubating
    DependencyConstraintSet getDependencyConstraints();

    /**
     * Adds an action to be executed before the dependencies in this set are resolved.
     *
     * @param action The action to execute.
     */
    void beforeResolve(Action<? super ResolvableDependencies> action);

    /**
     * Adds an action to be executed before the dependencies in this set are resolved.
     *
     * @param action The action to execute.
     */
    void beforeResolve(Closure action);

    /**
     * Adds an action to be executed after the dependencies of this set have been resolved.
     *
     * @param action The action to execute.
     */
    void afterResolve(Action<? super ResolvableDependencies> action);

    /**
     * Adds an action to be executed after the dependencies of this set have been resolved.
     *
     * @param action The action to execute.
     */
    void afterResolve(Closure action);

    /**
     * Returns the resolved dependency graph, performing the resolution if required. This will resolve the dependency graph but will not resolve or download the files.
     *
     * <p>You should note that when resolution fails, the exceptions are included in the {@link ResolutionResult} returned from this method. This method will not throw these exceptions.</p>
     *
     * @return the resolution result
     * @since 1.3
     */
    ResolutionResult getResolutionResult();

    /**
     * Returns the resolved artifacts, performing the resolution if required. This will resolve and download the files as required.
     *
     * @throws ResolveException On failure to resolve or download any artifact.
     * @since 3.4
     */
    @Override
    ArtifactCollection getArtifacts() throws ResolveException;

    /**
     * Returns a builder that can be used to define and access a filtered view of the resolved artifacts.
     * @return A view over the artifacts resolved for this set of dependencies.
     *
     * @since 3.4
     */
    ArtifactView artifactView(Action<? super ArtifactView.ViewConfiguration> configAction);
}
