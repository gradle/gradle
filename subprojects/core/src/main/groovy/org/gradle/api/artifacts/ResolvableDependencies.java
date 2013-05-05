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

/**
 * A set of {@link Dependency} objects which can be resolved to a set of {@code File} instances.
 */
public interface ResolvableDependencies {
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
     * Returns a {@link FileCollection} which contains the resolved set of files. The returned value is lazy, so dependency resolution is not performed until the contents of the
     * collection are queried.
     *
     * @return The collection. Never null.
     */
    FileCollection getFiles();

    /**
     * Returns the set of dependencies which will be resolved.
     *
     * @return the dependencies. Never null.
     */
    DependencySet getDependencies();

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
     * Returns an instance of {@link org.gradle.api.artifacts.result.ResolutionResult}
     * that gives access to the graph of the resolved dependencies.
     *
     * @return the resolution result
     * @since 1.3
     */
    @Incubating
    ResolutionResult getResolutionResult();
}
