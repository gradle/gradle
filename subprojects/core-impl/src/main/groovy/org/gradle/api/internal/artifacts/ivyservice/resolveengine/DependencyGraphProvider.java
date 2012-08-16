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
import org.gradle.api.internal.dependencygraph.api.DependencyGraph;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class DependencyGraphProvider implements ResolvedConfigurationListener {

    private ResolvedConfigurationIdentifier root;
    private Map<ModuleVersionIdentifier, Map<String, DefaultResolvedDependencyResult>> deps
            = new LinkedHashMap<ModuleVersionIdentifier, Map<String, DefaultResolvedDependencyResult>>();

    public void start(ResolvedConfigurationIdentifier root) {
        this.root = root;
    }

    public void resolvedConfiguration(ResolvedConfigurationIdentifier id, List<DefaultResolvedDependencyResult> dependencies) {
        if (!deps.containsKey(id.getId())) {
            deps.put(id.getId(), new LinkedHashMap<String, DefaultResolvedDependencyResult>());
        }
        if (!dependencies.isEmpty()) {
            //TODO SF I don't have to do this aggregation here. There is a simpler way.
            Map<String, DefaultResolvedDependencyResult> accumulatedDependencies = deps.get(id.getId());
            for (DefaultResolvedDependencyResult d : dependencies) {
                if (accumulatedDependencies.containsKey(d.toString())) {
                    accumulatedDependencies.get(d.toString()).appendConfigurations(d.getSelectedConfigurations());
                } else {
                    accumulatedDependencies.put(d.toString(), d);
                }
            }
        }
    }

    public DependencyGraph getGraph() {
        return new DefaultDependencyGraph(buildGraph());
    }

    private DefaultResolvedModuleVersionResult buildGraph() {
        Map<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult> visited = new HashMap<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult>();
        DefaultResolvedDependencyResult id = new DefaultResolvedDependencyResult(root.getId(), root.getId(), Sets.newHashSet(root.getConfiguration()));
        return buildNode(id, visited);
    }

    private DefaultResolvedModuleVersionResult buildNode(DefaultResolvedDependencyResult id, Map<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult> visited) {
        if (visited.containsKey(id)) {
            return visited.get(id);
        }
        DefaultResolvedModuleVersionResult node = id.getSelected();
        visited.put(id, node);

        Map<String, DefaultResolvedDependencyResult> theDeps = this.deps.get(id.getSelected().getId());
        if (theDeps == null) {
            return node;
        }
        for (DefaultResolvedDependencyResult sel : theDeps.values()) {
            buildNode(sel, visited);
            node.addDependency(sel);
        }

        return node;
    }
}
