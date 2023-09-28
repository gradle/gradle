/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Models the result of resolving dependency graph. Provides access to the root
 * of the dependency graph, as well as access to any failures that occurred while
 * building the graph.
 */
public interface VisitedGraphResults {

    /**
     * Returns true if any failures occurred while building these results.
     */
    boolean hasAnyFailure();

    /**
     * Visits all failures that occurred while resolving the graph.
     *
     * <p>No failures are visited if {@link #hasAnyFailure()} is false</p>
     */
    void visitFailures(Consumer<Throwable> visitor);

    /**
     * Returns all failures to resolve a dependency.
     * These failures are also accessible via the resolution result.
     */
    Set<UnresolvedDependency> getUnresolvedDependencies();

    /**
     * Returns an optional failure describing an error that occurred during resolution.
     * This failure does not encompass unresolved dependencies and is _not_ captured in the resolution result.
     */
    Optional<ResolveException> getResolutionFailure();

    /**
     * Returns the root of the dependency graph.
     */
    MinimalResolutionResult getResolutionResult();
}
