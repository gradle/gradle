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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyGraphPathResolver {

    public static Collection<List<Describable>> calculatePaths(
        List<DependencyGraphNode> fromNodes,
        DependencyGraphNode toNode,
        DomainObjectContext owner
    ) {
        // Include the shortest path from each version that has a direct dependency on the broken dependency, back to the root

        Map<ResolvedGraphComponent, List<Describable>> shortestPaths = new LinkedHashMap<>();
        List<Describable> rootPath = new ArrayList<>();
        rootPath.add(Describables.of(owner.getDisplayName()));
        shortestPaths.put(toNode.getOwner(), rootPath);

        Set<DependencyGraphComponent> directDependees = new LinkedHashSet<>();
        for (DependencyGraphNode node : fromNodes) {
            directDependees.add(node.getOwner());
        }

        Set<DependencyGraphComponent> seen = new HashSet<>();
        LinkedList<DependencyGraphComponent> queue = new LinkedList<>(directDependees);
        while (!queue.isEmpty()) {
            DependencyGraphComponent version = queue.getFirst();
            if (version == toNode.getOwner()) {
                queue.removeFirst();
            } else if (seen.add(version)) {
                for (DependencyGraphComponent incomingVersion : version.getDependents()) {
                    queue.add(0, incomingVersion);
                }
            } else {
                queue.remove();
                List<Describable> shortest = null;
                for (DependencyGraphComponent incomingVersion : version.getDependents()) {
                    List<Describable> candidate = shortestPaths.get(incomingVersion);
                    if (candidate == null) {
                        continue;
                    }
                    if (shortest == null) {
                        shortest = candidate;
                    } else if (shortest.size() > candidate.size()) {
                        shortest = candidate;
                    }

                }
                if (shortest == null) {
                    continue;
                }
                List<Describable> path = new ArrayList<>(shortest);
                path.add(Describables.of(version.getComponentId().getDisplayName()));
                shortestPaths.put(version, path);
            }
        }

        List<List<Describable>> paths = new ArrayList<>();
        for (DependencyGraphComponent version : directDependees) {
            List<Describable> path = shortestPaths.get(version);
            paths.add(path);
        }
        return paths;
    }
}
