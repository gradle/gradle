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

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Models the result of resolving dependency graph. Provides access to the root
 * of the dependency graph, as well as access to any failures that occurred while
 * building the graph.
 */
public interface VisitedGraphResults {

    /**
     * Returns true if any resolution failures occurred while building these results.
     */
    boolean hasResolutionFailure();

    /**
     * Returns an optional exception describing all resolution failures that occurred while building these results.
     * This failure encapsulates all failures provided by {@link #getUnresolvedDependencies()} and
     * {@link #getAdditionalResolutionFailure()}.
     *
     * @return null if {@link #hasResolutionFailure()} is false.
     */
    @Nullable
    ResolveException getResolutionFailure();

    /**
     * Visits all failures that occurred while resolving the graph.
     */
    void visitResolutionFailures(Consumer<Throwable> visitor);

    /**
     * Returns all resolution failures.
     */
    Set<UnresolvedDependency> getUnresolvedDependencies();

    /**
     * Returns an optional failure describing all failures encountered during
     * resolution that are _not_ captured in the resolution result. Since the
     * resolution result provides access to unresolved dependencies, this failure
     * captures anything not provided by {@link #getUnresolvedDependencies()}.
     */
    @Nullable
    ResolveException getAdditionalResolutionFailure();

    /**
     * Returns the root of the dependency graph.
     */
    MinimalResolutionResult getResolutionResult();
}
