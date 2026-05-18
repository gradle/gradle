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

import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph;
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.lazy.Lazy;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default implementation of {@link VisitedGraphResults}.
 */
public class DefaultVisitedGraphResults implements VisitedGraphResults {

    private final ImmutableAttributes requestAttributes;
    private final Supplier<GraphStructure> graphStructureSource;
    private final Set<UnresolvedDependency> unresolvedDependencies;

    private final Supplier<ResolvedGraphResult> resolvedGraphResultSource;

    public DefaultVisitedGraphResults(
        ResolvedDependencyGraph resolvedDependencyGraph,
        Set<UnresolvedDependency> unresolvedDependencies
    ) {
        this.requestAttributes = resolvedDependencyGraph.requestAttributes();
        this.graphStructureSource = resolvedDependencyGraph.graphSource();
        this.unresolvedDependencies = unresolvedDependencies;

        this.resolvedGraphResultSource = Lazy.unsafe().of(() ->
            new ResolvedGraphResult(
                graphStructureSource.get(),
                resolvedDependencyGraph.availableVariantsByComponent()
            )
        );
    }

    @Override
    public ImmutableAttributes getRequestedAttributes() {
        return requestAttributes;
    }

    @Override
    public boolean hasAnyFailure() {
        return !unresolvedDependencies.isEmpty();
    }

    @Override
    public void visitFailures(Consumer<Throwable> visitor) {
        for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
            visitor.accept(unresolvedDependency.getProblem());
        }
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    @Override
    public Supplier<GraphStructure> getGraphStructureSource() {
        return graphStructureSource;
    }

    @Override
    public Supplier<ResolvedGraphResult> getResolvedGraphResultSource() {
        return resolvedGraphResultSource;
    }

}
