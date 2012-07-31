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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.dependencygraph.api.DependencyGraphListener;
import org.gradle.api.internal.dependencygraph.api.DependencyGraphNode;

import java.util.*;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DependencyGraphProvider implements ResolvedConfigurationListener {

    private ResolvedConfigurationIdentifier root;
    private Map<ModuleVersionIdentifier, Map<String, DefaultDependencyModule>> deps
            = new LinkedHashMap<ModuleVersionIdentifier, Map<String, DefaultDependencyModule>>();
    private final DependencyGraphListener dependencyGraphListener;

    public DependencyGraphProvider(DependencyGraphListener dependencyGraphListener) {
        this.dependencyGraphListener = dependencyGraphListener;
    }

    public void start(ResolvedConfigurationIdentifier root) {
        this.root = root;
    }

    public void resolvedConfiguration(ResolvedConfigurationIdentifier id, List<DefaultDependencyModule> dependencies) {
        if (!deps.containsKey(id.getId())) {
            deps.put(id.getId(), new LinkedHashMap<String, DefaultDependencyModule>());
        }
        if (!dependencies.isEmpty()) {
            //TODO SF I don't have to do this aggregation here. There is a simpler way.
            Map<String, DefaultDependencyModule> accumulatedDependencies = deps.get(id.getId());
            for (DefaultDependencyModule d : dependencies) {
                if (accumulatedDependencies.containsKey(d.toString())) {
                    accumulatedDependencies.get(d.toString()).appendConfigurations(d.getConfigurations());
                } else {
                    accumulatedDependencies.put(d.toString(), d);
                }
            }
        }
    }

    public void completed() {
        dependencyGraphListener.whenResolved(buildGraph());
    }

    public static class DefaultDependencyNode implements DependencyGraphNode {
        private final DefaultDependencyModule id;
        private final Set<DefaultDependencyNode> dependencies = new LinkedHashSet<DefaultDependencyNode>();

        public DefaultDependencyNode(DefaultDependencyModule id) {
            this.id = id;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            Set<ModuleVersionIdentifier> visited = new HashSet<ModuleVersionIdentifier>();
            printNode(sb, this, "", visited);
            return sb.toString();
        }

        private static void printNode(StringBuilder sb, DefaultDependencyNode node, String level, Set<ModuleVersionIdentifier> visited) {
            sb.append("\n" + level + node.id);
            if (!visited.add(node.id.getAsked())) {
                sb.append(" (*)");
                return;
            }
            for (DefaultDependencyNode dependency : node.dependencies) {
                printNode(sb, dependency, level + "  ", visited);
            }
        }

        public DefaultDependencyModule getId() {
            return id;
        }

        public Set<DefaultDependencyNode> getDependencies() {
            return dependencies;
        }
    }

    public DefaultDependencyNode buildGraph() {
        if (root == null) {
            return null; //TODO SF ugly
        }
        Map<DefaultDependencyModule, DefaultDependencyNode> visited = new HashMap<DefaultDependencyModule, DefaultDependencyNode>();
        DefaultDependencyModule id = new DefaultDependencyModule(root.getId(), root.getId(), Sets.newHashSet(root.getConfiguration()));
        return buildNode(id, visited);
    }

    private DefaultDependencyNode buildNode(DefaultDependencyModule id, Map<DefaultDependencyModule, DefaultDependencyNode> visited) {
        if (visited.containsKey(id)) {
            return visited.get(id);
        }
        DefaultDependencyNode node = new DefaultDependencyNode(id);
        visited.put(id, node);

        Map<String, DefaultDependencyModule> theDeps = this.deps.get(id.getSelected());
        if (theDeps == null) {
            return node;
        }
        for (DefaultDependencyModule sel : theDeps.values()) {
            node.dependencies.add(buildNode(sel, visited));
        }

        return node;
    }

    @Override
    public String toString() {
        if (root == null) {
            return "";
        }
        DefaultDependencyNode node = buildGraph();
        return node.toString();
    }
}
