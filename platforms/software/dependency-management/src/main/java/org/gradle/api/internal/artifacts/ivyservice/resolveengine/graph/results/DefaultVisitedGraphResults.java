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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default implementation of {@link VisitedGraphResults}.
 */
public class DefaultVisitedGraphResults implements VisitedGraphResults {

    private final MinimalResolutionResult resolutionResult;
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final ResolveException resolutionFailure;

    public DefaultVisitedGraphResults(
        MinimalResolutionResult resolutionResult,
        Set<UnresolvedDependency> unresolvedDependencies,
        @Nullable ResolveException resolutionFailure
    ) {
        this.resolutionResult = resolutionResult;
        this.unresolvedDependencies = unresolvedDependencies;
        this.resolutionFailure = resolutionFailure;
    }

    @Override
    public boolean hasAnyFailure() {
        return !unresolvedDependencies.isEmpty() || resolutionFailure != null;
    }

    @Override
    public void visitFailures(Consumer<Throwable> visitor) {
        for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
            visitor.accept(unresolvedDependency.getProblem());
        }
        if (resolutionFailure != null) {
            visitor.accept(resolutionFailure);
        }
    }

    @Override
    public MinimalResolutionResult getResolutionResult() {
        return resolutionResult;
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    @Override
    public Optional<ResolveException> getResolutionFailure() {
        return Optional.ofNullable(resolutionFailure);
    }
}
