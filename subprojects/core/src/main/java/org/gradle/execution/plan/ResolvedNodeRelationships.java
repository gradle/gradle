/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.execution.plan;

import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds the resolved dependency relationships for a {@link LocalTaskNode}, without mutating the graph.
 * Used to separate the expensive resolution phase from the cheap graph mutation phase
 * during parallel discovery.
 */
@NullMarked
public class ResolvedNodeRelationships {

    private final LocalTaskNode node;
    private final Set<Node> dependencies;
    private final Set<Node> lifecycleDependencies;
    private final Set<Node> finalizedBy;
    private final Set<Node> mustRunAfter;
    private final Set<Node> shouldRunAfter;

    public ResolvedNodeRelationships(
        LocalTaskNode node,
        Set<Node> dependencies,
        Set<Node> lifecycleDependencies,
        Set<Node> finalizedBy,
        Set<Node> mustRunAfter,
        Set<Node> shouldRunAfter
    ) {
        this.node = node;
        this.dependencies = dependencies;
        this.lifecycleDependencies = lifecycleDependencies;
        this.finalizedBy = finalizedBy;
        this.mustRunAfter = mustRunAfter;
        this.shouldRunAfter = shouldRunAfter;
    }

    public LocalTaskNode getNode() {
        return node;
    }

    public Set<Node> getDependencies() {
        return dependencies;
    }

    public Set<Node> getLifecycleDependencies() {
        return lifecycleDependencies;
    }

    public Set<Node> getFinalizedBy() {
        return finalizedBy;
    }

    public Set<Node> getMustRunAfter() {
        return mustRunAfter;
    }

    public Set<Node> getShouldRunAfter() {
        return shouldRunAfter;
    }

    /**
     * Returns a new instance with all {@link DeferredCrossProjectNode} placeholders replaced
     * by their resolved real nodes. Returns {@code this} if no placeholders are present.
     */
    ResolvedNodeRelationships substitutePlaceholders() {
        Set<Node> newDeps = substitutePlaceholders(dependencies);
        Set<Node> newLifecycle = substitutePlaceholders(lifecycleDependencies);
        Set<Node> newFinalizedBy = substitutePlaceholders(finalizedBy);
        Set<Node> newMustRunAfter = substitutePlaceholders(mustRunAfter);
        Set<Node> newShouldRunAfter = substitutePlaceholders(shouldRunAfter);
        if (newDeps == dependencies
            && newLifecycle == lifecycleDependencies
            && newFinalizedBy == finalizedBy
            && newMustRunAfter == mustRunAfter
            && newShouldRunAfter == shouldRunAfter) {
            return this;
        }
        return new ResolvedNodeRelationships(node, newDeps, newLifecycle, newFinalizedBy, newMustRunAfter, newShouldRunAfter);
    }

    private static Set<Node> substitutePlaceholders(Set<Node> original) {
        if (original.stream().noneMatch(DeferredCrossProjectNode.class::isInstance)) {
            return original;
        }
        Set<Node> result = new HashSet<>();
        for (Node n : original) {
            if (n instanceof DeferredCrossProjectNode) {
                result.addAll(((DeferredCrossProjectNode) n).getResolvedNodes());
            } else {
                result.add(n);
            }
        }
        return result;
    }
}
