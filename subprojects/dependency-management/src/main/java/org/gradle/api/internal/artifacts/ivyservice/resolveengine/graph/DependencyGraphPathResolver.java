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

import org.gradle.api.artifacts.component.ComponentIdentifier;

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

    public static Collection<List<ComponentIdentifier>> calculatePaths(List<DependencyGraphNode> fromNodes, DependencyGraphNode toNode) {
        // Include the shortest path from each version that has a direct dependency on the broken dependency, back to the root

        Map<ResolvedGraphComponent, List<ComponentIdentifier>> shortestPaths = new LinkedHashMap<>();
        List<ComponentIdentifier> rootPath = new ArrayList<>();
        rootPath.add(toNode.getOwner().getComponentId());
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
                List<ComponentIdentifier> shortest = null;
                for (DependencyGraphComponent incomingVersion : version.getDependents()) {
                    List<ComponentIdentifier> candidate = shortestPaths.get(incomingVersion);
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
                List<ComponentIdentifier> path = new ArrayList<>(shortest);
                path.add(version.getComponentId());
                shortestPaths.put(version, path);
            }
        }

        List<List<ComponentIdentifier>> paths = new ArrayList<>();
        for (DependencyGraphComponent version : directDependees) {
            List<ComponentIdentifier> path = shortestPaths.get(version);
            paths.add(path);
        }
        return paths;
    }
}
