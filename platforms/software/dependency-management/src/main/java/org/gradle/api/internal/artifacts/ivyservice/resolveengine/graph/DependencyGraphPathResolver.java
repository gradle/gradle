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

    /**
     * Calculates the shortest "incoming path" from each direct dependee of a broken dependency
     * back to the resolution root, used to render the "Required by:" tail in
     * {@link org.gradle.internal.resolve.ModuleVersionResolveException#getMessage}.
     *
     * <p>Some direct dependees can have an empty {@code getDependents()} chain at the time
     * this runs — most commonly because the dependee's module was pushed back to "pending"
     * state during resolution and {@code ModuleResolveState.clearIncomingAttachedConstraints}
     * removed all of its constraint-only incoming edges. That is intentional engine design
     * (the module is "no longer part of the graph"), so the back-link is genuinely gone by
     * the time path resolution runs. The handling for that case is documented at the
     * relevant assembly site below; see
     * <a href="https://github.com/gradle/gradle/issues/36284">issue 36284</a> for details.
     *
     * <p><b>Contract:</b> the returned collection's element lists are guaranteed non-null
     * and non-empty. Direct dependees whose path back to the root cannot be reconstructed
     * are omitted — there is no entry for them rather than a null entry.
     */
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
            // Skip direct dependees whose path back to root could not be reconstructed
            // (typically because the dependee's module returned to "pending" state during
            // resolution and ModuleResolveState.clearIncomingAttachedConstraints removed
            // its constraint-only incoming edges — see issue 36284). Including null here
            // would break consumers like ModuleVersionResolveException.getMessage(); the
            // failure's "Required by:" tail simply omits these dependees.
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }
}
