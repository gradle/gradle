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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyGraphPathResolver {

    public static Collection<List<Describable>> calculatePaths(
        List<DependencyGraphNode> fromNodes,
        DependencyGraphNode toNode,
        DomainObjectContext owner
    ) {
        // Compute the shortest path from each component that has a direct dependency on the broken
        // dependency back to the root component. Each search is an independent BFS in the reverse
        // ("dependents") direction, which is robust against cycles in the resolved graph (e.g.
        // mutually-dependent KMP variants like room-runtime <-> room-common-jvm).

        DependencyGraphComponent root = toNode.getOwner();
        Describable rootDescribable = Describables.of(owner.getDisplayName());

        Set<DependencyGraphComponent> directDependees = new LinkedHashSet<>();
        for (DependencyGraphNode node : fromNodes) {
            directDependees.add(node.getOwner());
        }

        List<List<Describable>> paths = new ArrayList<>();
        for (DependencyGraphComponent dependee : directDependees) {
            List<Describable> path = shortestPathToRoot(dependee, root, rootDescribable);
            if (path != null) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static @Nullable List<Describable> shortestPathToRoot(
        DependencyGraphComponent start,
        DependencyGraphComponent root,
        Describable rootDescribable
    ) {
        if (start == root) {
            List<Describable> single = new ArrayList<>(1);
            single.add(rootDescribable);
            return single;
        }
        Map<DependencyGraphComponent, DependencyGraphComponent> predecessor = new HashMap<>();
        predecessor.put(start, null);
        Deque<DependencyGraphComponent> queue = new ArrayDeque<>();
        queue.add(start);
        boolean reachedRoot = false;
        while (!queue.isEmpty()) {
            DependencyGraphComponent current = queue.removeFirst();
            if (current == root) {
                reachedRoot = true;
                break;
            }
            // TODO: this walks every dependents edge including constraint edges; constraints
            //  don't "pull in" a component, so paths that traverse them aren't useful to users.
            //  Restricting to non-constraint edges would shift many existing test expectations,
            //  so it is deferred.
            for (DependencyGraphComponent next : current.getDependents()) {
                if (!predecessor.containsKey(next)) {
                    predecessor.put(next, current);
                    queue.addLast(next);
                }
            }
        }
        if (!reachedRoot) {
            return null;
        }

        // Walk predecessors from root back to start; this naturally produces [root, ..., start].
        List<DependencyGraphComponent> chain = new ArrayList<>();
        for (DependencyGraphComponent walk = root; walk != null; walk = predecessor.get(walk)) {
            chain.add(walk);
        }

        // Output format expected by ModuleVersionResolveException#getMessage:
        // [<rootDisplayName>, <intermediate componentIds...>, <directDependee componentId>].
        // Substitute rootDescribable for root's componentId, then emit the rest in order.
        List<Describable> result = new ArrayList<>(chain.size());
        result.add(rootDescribable);
        for (int i = 1; i < chain.size(); i++) {
            result.add(Describables.of(chain.get(i).getComponentId().getDisplayName()));
        }
        return result;
    }
}
