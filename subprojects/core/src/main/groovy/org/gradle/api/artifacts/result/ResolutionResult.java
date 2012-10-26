/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.artifacts.result;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.util.Set;

/**
 * Contains the information about the resolution result.
 * Gives access to the resolved dependency graph.
 * In future it will contain more convenience methods and
 * other useful information about the resolution results.
 */
@Incubating
public interface ResolutionResult {

    /**
     * Gives access to the resolved dependency graph.
     * You can walk the graph recursively from the root to obtain information about resolved dependencies.
     * For example, Gradle's built-in 'dependencies' uses it to render the dependency tree.
     *
     * @return the root node of the resolved dependency graph
     */
    ResolvedModuleVersionResult getRoot();

    /**
     * Retrieves all dependencies, including unresolved dependencies.
     * Resolved dependencies are represented by instances of {@link ResolvedDependencyResult},
     * unresolved dependencies by {@link UnresolvedDependencyResult}.
     *
     * In dependency graph terminology, this method returns the edges of the graph.
     *
     * @return all dependencies, including unresolved dependencies.
     */
    Set<? extends DependencyResult> getAllDependencies();

    /**
     * Applies given action for each dependency.
     * An instance of {@link DependencyResult} is passed as parameter to the action.
     *
     * @param action - action that is applied for each dependency
     */
    void allDependencies(Action<? super DependencyResult> action);

    /**
     * Applies given closure for each dependency.
     * An instance of {@link DependencyResult} is passed as parameter to the closure.
     *
     * @param closure - closure that is applied for each dependency
     */
    void allDependencies(Closure closure);

    /**
     * Retrieves all instances of {@link ResolvedModuleVersionResult} from the graph,
     * e.g. all nodes of the dependency graph.
     *
     * @return all nodes of the dependency graph.
     */
    Set<ResolvedModuleVersionResult> getAllModuleVersions();

    /**
     * Applies given action for each module.
     * An instance of {@link ResolvedModuleVersionResult} is passed as parameter to the action.
     *
     * @param action - action that is applied for each module
     */
    void allModuleVersions(Action<? super ResolvedModuleVersionResult> action);

    /**
     * Applies given closure for each module.
     * An instance of {@link ResolvedModuleVersionResult} is passed as parameter to the closure.
     *
     * @param closure - closure that is applied for each module
     */
    void allModuleVersions(Closure closure);
}