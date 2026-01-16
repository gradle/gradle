/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.Describable;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.internal.Describables;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

/**
 * Utility class for calculating shortest dependency paths in a dependency graph.
 *
 * <p>This resolver is primarily used to generate meaningful error messages when dependency
 * resolution fails, by showing the chain of dependencies that leads to a problematic
 * dependency.</p>
 *
 * <p>The algorithm performs a breadth-first search (BFS) backwards through the dependency
 * graph, starting from direct dependents and working towards the root, to find the
 * shortest paths.</p>
 */
public class DependencyGraphPathResolver {

    /**
     * Calculates the shortest dependency paths from each starting node to a target node.
     * This is typically used to show dependency resolution paths for error messages.
     *
     * @param nodes The starting nodes (usually direct dependents of a problematic dependency)
     * @param target The target node to find paths to (usually the problematic dependency itself)
     * @param owner The domain object context representing the root/owner of the dependency graph
     * @return A collection of paths, where each path is a list of describable elements
     * representing the dependency chain from root to the starting node
     */
    public static Collection<List<Describable>> calculatePaths(
        List<DependencyGraphNode> nodes,
        @Nullable DependencyGraphNode target,
        DomainObjectContext owner
    ) {
        if (target == null) {
            return emptyList();
        }
        Map<DependencyGraphComponent, List<Describable>> shortestPaths = getDependencyGraphComponentListMap(target, owner);
        return calculateShortestPathsFromDependees(
            nodes
                .stream()
                .map(DependencyGraphNode::getOwner)
                .collect(toCollection(LinkedHashSet::new)), target.getOwner(), shortestPaths)
            .stream()
            .map(shortestPaths::get)
            .filter(Objects::nonNull)
            .collect(toList());
    }

    /**
     * Creates and initializes the shortest paths map with the root path.
     *
     * @param target The target node
     * @param owner The domain object context representing the root
     * @return An initialized map with the root path for the target node
     */
    private static Map<DependencyGraphComponent, List<Describable>> getDependencyGraphComponentListMap(
        DependencyGraphNode target,
        DomainObjectContext owner
    ) {
        Map<DependencyGraphComponent, List<Describable>> shortestPaths = new LinkedHashMap<>();
        shortestPaths.put(target.getOwner(), singletonList(Describables.of(owner.getDisplayName())));
        return shortestPaths;
    }

    /**
     * Calculates the shortest paths from each direct dependent to the target using BFS.
     * The algorithm works backwards from dependents to their dependencies.
     *
     * @param directDependees The set of direct dependents to calculate paths for
     * @param target The target node to find paths to
     * @param shortestPaths Map to store the calculated shortest paths for each node
     */
    private static Set<DependencyGraphComponent> calculateShortestPathsFromDependees(
        Set<DependencyGraphComponent> directDependees,
        DependencyGraphComponent target,
        Map<DependencyGraphComponent, List<Describable>> shortestPaths
    ) {
        Set<DependencyGraphComponent> seen = new HashSet<>();
        List<DependencyGraphComponent> queue = new LinkedList<>(directDependees);
        while (!queue.isEmpty()) {
            processQueueNode(target, shortestPaths, queue, seen);
        }
        return directDependees;
    }

    /**
     * Processes the current node in the BFS traversal.
     *
     * @param target The target node to find paths to
     * @param shortestPaths Map storing the calculated shortest paths
     * @param queue The BFS queue of nodes to process
     * @param seen Set of nodes that have already been processed
     */
    private static void processQueueNode(
        DependencyGraphComponent target,
        Map<DependencyGraphComponent, List<Describable>> shortestPaths,
        List<DependencyGraphComponent> queue,
        Set<DependencyGraphComponent> seen
    ) {
        DependencyGraphComponent current = queue.get(0);
        if (current == target) {
            queue.remove(0);
        } else if (seen.add(current)) {
            addDependentsToQueue(queue, current);
        } else {
            calculateAndStoreShortestPath(shortestPaths, queue, current);
        }
    }

    /**
     * Calculates and stores the shortest path for a previously seen node.
     *
     * @param shortestPaths Map to store calculated paths
     * @param queue The BFS queue
     * @param current The current node being processed
     */
    private static void calculateAndStoreShortestPath(
        Map<DependencyGraphComponent, List<Describable>> shortestPaths,
        List<DependencyGraphComponent> queue,
        DependencyGraphComponent current
    ) {
        queue.remove(0);
        List<Describable> shortestPathFromDependents = null;
        for (DependencyGraphComponent dependent : current.getDependents()) {
            List<Describable> candidatePath = shortestPaths.get(dependent);
            if (candidatePath != null && (shortestPathFromDependents == null ||
                candidatePath.size() < shortestPathFromDependents.size())) {
                shortestPathFromDependents = candidatePath;
            }
        }
        if (shortestPathFromDependents != null) {
            shortestPaths.put(
                current,
                concat(shortestPathFromDependents.stream(), Stream.of(Describables.of(current.getComponentId().getDisplayName()))).collect(toList()));
        }
    }

    /**
     * Adds all dependents of a newly discovered node to the front of the queue.
     *
     * @param queue The BFS queue
     * @param current The current node whose dependents need to be added
     */
    private static void addDependentsToQueue(
        List<DependencyGraphComponent> queue,
        DependencyGraphComponent current
    ) {
        for (DependencyGraphComponent dependent : current.getDependents()) {
            queue.add(0, dependent);
        }
    }
}
