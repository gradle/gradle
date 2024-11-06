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

import com.google.common.collect.Iterables;
import org.gradle.internal.collect.PersistentList;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A directed graph implementation that detects cycles dynamically as edges are added.
 *
 * <p>This class facilitates building a directed graph by adding nodes and directed edges between them. Each
 * node can be designated as "acyclic" by adding it to the graph using {@link #addAcyclicNode(Object)}.
 * As edges are introduced, the graph checks for cycles that would invalidate the acyclic structure.</p>
 *
 * <p>When an edge is added using {@link #addEdge(Object, Object)}, the graph does not immediately check for
 * cycles. However, a call to {@link #findFirstInvalidCycle()} initiates a search to detect any invalid cycles
 * among the acyclic nodes, using a depth-first search (DFS) approach. If a cycle is detected, it returns an
 * {@link Optional} containing the {@link Cycle}, which details the sequence of nodes forming the cycle.
 * Otherwise, it returns an empty {@code Optional}.</p>
 *
 * <p>The graph is represented internally as a {@link Map}, where each node is mapped to a set of its referrers
 * (i.e., nodes that point to it).
 *
 * @param <T> the type of nodes in the graph
 */
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
    private final Set<T> acyclicNodes = new LinkedHashSet<>();

    public void addAcyclicNode(T node) {
        acyclicNodes.add(node);
    }

    public void addEdge(T from, T to) {
        referrersOf(to).add(from);
    }

    public Optional<Cycle<T>> findFirstInvalidCycle() {
        for (T node : acyclicNodes) {
            Optional<Cycle<T>> cycle = findCycle(node, node);
            if (cycle.isPresent()) {
                return cycle;
            }
        }
        return Optional.empty();
    }

    private Optional<Cycle<T>> findCycle(T from, T to) {
        return findCycle(from, to, PersistentList.of(from));
    }

    private Optional<Cycle<T>> findCycle(T from, T to, PersistentList<T> path) {
        Set<T> referrers = graph.get(from);
        if (referrers == null) {
            return Optional.empty();
        }
        if (referrers.contains(to)) {
            return Optional.of(new Cycle<>(path.plus(to)));
        }
        for (T referrer : referrers) {
            if (Iterables.contains(path, referrer)) {
                continue;
            }
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
