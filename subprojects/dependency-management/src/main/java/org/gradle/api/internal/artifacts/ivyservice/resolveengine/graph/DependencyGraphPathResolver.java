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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ModuleVersionSelection;

import java.util.*;

public class DependencyGraphPathResolver {

    public static Collection<List<ModuleVersionIdentifier>> calculatePaths(List<DependencyGraphNode> fromNodes, DependencyGraphNode toNode) {
        // Include the shortest path from each version that has a direct dependency on the broken dependency, back to the root

        Map<ModuleVersionSelection, List<ModuleVersionIdentifier>> shortestPaths = new LinkedHashMap<ModuleVersionSelection, List<ModuleVersionIdentifier>>();
        List<ModuleVersionIdentifier> rootPath = new ArrayList<ModuleVersionIdentifier>();
        rootPath.add(toNode.toId());
        shortestPaths.put(toNode.getSelection(), rootPath);

        Set<DependencyGraphBuilder.ModuleVersionResolveState> directDependees = new LinkedHashSet<DependencyGraphBuilder.ModuleVersionResolveState>();
        for (DependencyGraphNode node : fromNodes) {
            DependencyGraphBuilder.ConfigurationNode rawNode = (DependencyGraphBuilder.ConfigurationNode) node;
            directDependees.add(rawNode.moduleRevision);
        }

        Set<DependencyGraphBuilder.ModuleVersionResolveState> seen = new HashSet<DependencyGraphBuilder.ModuleVersionResolveState>();
        LinkedList<DependencyGraphBuilder.ModuleVersionResolveState> queue = new LinkedList<DependencyGraphBuilder.ModuleVersionResolveState>();
        queue.addAll(directDependees);
        while (!queue.isEmpty()) {
            DependencyGraphBuilder.ModuleVersionResolveState version = queue.getFirst();
            if (version == toNode.getSelection()) {
                queue.removeFirst();
            } else if (seen.add(version)) {
                for (DependencyGraphBuilder.ModuleVersionResolveState incomingVersion : version.getIncoming()) {
                    queue.add(0, incomingVersion);
                }
            } else {
                queue.remove();
                List<ModuleVersionIdentifier> shortest = null;
                for (DependencyGraphBuilder.ModuleVersionResolveState incomingVersion : version.getIncoming()) {
                    List<ModuleVersionIdentifier> candidate = shortestPaths.get(incomingVersion);
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
                List<ModuleVersionIdentifier> path = new ArrayList<ModuleVersionIdentifier>();
                path.addAll(shortest);
                path.add(version.id);
                shortestPaths.put(version, path);
            }
        }

        List<List<ModuleVersionIdentifier>> paths = new ArrayList<List<ModuleVersionIdentifier>>();
        for (DependencyGraphBuilder.ModuleVersionResolveState version : directDependees) {
            List<ModuleVersionIdentifier> path = shortestPaths.get(version);
            paths.add(path);
        }
        return paths;
    }
}
