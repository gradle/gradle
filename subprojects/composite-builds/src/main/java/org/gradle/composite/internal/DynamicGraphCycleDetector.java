/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.collect.PersistentList;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A directed graph implementation that dynamically detects and prevents cycles as edges are added.
 *
 * <p>This class allows the construction of a directed graph where nodes are added along with their dependencies
 * (represented as directed edges). As each edge is added, the graph checks if introducing the edge would result
 * in a cycle. If a cycle is detected, the edge is not added, and an {@link Optional} containing the cycle
 * (represented as a {@link Cycle}) is returned.</p>
 *
 * <p>This class is useful in scenarios where dynamically managing dependencies between entities is required,
 * and introducing cycles in the dependency graph must be avoided.</p>
 *
 * <p>The graph is represented internally as a {@link Map}, where each node is mapped to a set of its referrers
 * (i.e., nodes that point to it). Cycle detection uses a depth-first search (DFS) approach to trace paths
 * between nodes when new edges are added.</p>
 *
 * @param <T> the type of nodes in the graph
 * */
class DynamicGraphCycleDetector<T> {

    public static class Cycle<T> {

        private final PersistentList<T> path;

        public Cycle(PersistentList<T> path) {
            this.path = path;
        }

        public String format(Function<T, String> toString) {
            StringBuilder builder = new StringBuilder();
            for (T segment : path) {
                if (builder.length() > 0) {
                    builder.append(" -> ");
                }
                builder.append(toString.apply(segment));
            }
            return builder.toString();
        }

        public Cycle<T> plus(T from) {
            return new Cycle<>(path.plus(from));
        }
    }

    private final Map<T, Set<T>> graph = new HashMap<>();

    public synchronized Optional<Cycle<T>> addEdge(T from, T to) {
        if (from.equals(to)) {
            return Optional.of(new Cycle<>(PersistentList.of(from, to)));
        }
        Optional<Cycle<T>> cycle = findCycle(from, to);
        if (cycle.isPresent()) {
            return cycle.map(it -> it.plus(from));
        }
        referrersOf(to).add(from);
        return Optional.empty();
    }

    @Nonnull
    private Optional<Cycle<T>> findCycle(T from, T to) {
        return findCycle(from, to, PersistentList.of(from));
    }

    @Nonnull
    private Optional<Cycle<T>> findCycle(T from, T to, PersistentList<T> path) {
        Set<T> referrers = referrersOf(from);
        if (referrers.contains(to)) {
            return Optional.of(new Cycle<>(path.plus(to)));
        }
        for (T referrer : referrers) {
            Optional<Cycle<T>> cycle = findCycle(referrer, to, path.plus(referrer));
            if (cycle.isPresent()) {
                return cycle;
            }
        }

        return Optional.empty();
    }

    private Set<T> referrersOf(T from) {
        return graph.computeIfAbsent(from, k -> new LinkedHashSet<>());
    }
}
