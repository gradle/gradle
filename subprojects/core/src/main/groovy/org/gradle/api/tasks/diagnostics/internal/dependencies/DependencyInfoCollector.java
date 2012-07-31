/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.dependencies;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.dependencygraph.DependencyGraphListener;
import org.gradle.api.internal.dependencygraph.DependencyModule;

import java.util.*;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DependencyInfoCollector implements DependencyGraphListener {

    private ResolvedConfigurationIdentifier root;
    private Map<ModuleVersionIdentifier, Map<String, DependencyModule>> deps
            = new LinkedHashMap<ModuleVersionIdentifier, Map<String, DependencyModule>>();

    public void resolvedDependency(ResolvedConfigurationIdentifier root, ResolvedConfigurationIdentifier id, List<DependencyModule> dependencies) {
        this.root = root;
        if (!deps.containsKey(id.getId())) {
            deps.put(id.getId(), new LinkedHashMap<String, DependencyModule>());
        }
        if (!dependencies.isEmpty()) {
            //TODO SF I don't have to do this aggregation here. There is a simpler way.
            Map<String, DependencyModule> accumulatedDependencies = deps.get(id.getId());
            for (DependencyModule d : dependencies) {
                if (accumulatedDependencies.containsKey(d.toString())) {
                    accumulatedDependencies.get(d.toString()).appendConfigurations(d.getConfigurations());
                } else {
                    accumulatedDependencies.put(d.toString(), d);
                }
            }
        }
    }

    public static class DependencyNode {
        private final DependencyModule id;
        private final Set<DependencyNode> dependencies = new LinkedHashSet<DependencyNode>();

        public DependencyNode(DependencyModule id) {
            this.id = id;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            Set<ModuleVersionIdentifier> visited = new HashSet<ModuleVersionIdentifier>();
            printNode(sb, this, "", visited);
            return sb.toString();
        }

        private static void printNode(StringBuilder sb, DependencyNode node, String level, Set<ModuleVersionIdentifier> visited) {
            sb.append("\n" + level + node.id);
            if (!visited.add(node.id.getAsked())) {
                sb.append(" (*)");
                return;
            }
            for (DependencyNode dependency : node.dependencies) {
                printNode(sb, dependency, level + "  ", visited);
            }
        }

        public DependencyModule getId() {
            return id;
        }

        public Set<DependencyNode> getDependencies() {
            return dependencies;
        }
    }

    public DependencyNode buildGraph() {
        if (root == null) {
            return null; //TODO SF ugly
        }
        Map<DependencyModule, DependencyNode> visited = new HashMap<DependencyModule, DependencyNode>();
        DependencyModule id = new DependencyModule(root.getId(), root.getId(), Sets.newHashSet(root.getConfiguration()));
        return buildNode(id, visited);
    }

    private DependencyNode buildNode(DependencyModule id, Map<DependencyModule, DependencyNode> visited) {
        if (visited.containsKey(id)) {
            return visited.get(id);
        }
        DependencyNode node = new DependencyNode(id);
        visited.put(id, node);

        Map<String, DependencyModule> theDeps = this.deps.get(id.getSelected());
        if (theDeps == null) {
            return node;
        }
        for (DependencyModule sel : theDeps.values()) {
            node.dependencies.add(buildNode(sel, visited));
        }

        return node;
    }

    @Override
    public String toString() {
        if (root == null) {
            return "";
        }
        DependencyNode node = buildGraph();
        return node.toString();
    }
}
