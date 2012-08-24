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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedModuleVersionResult;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.collect.Sets.newHashSet;
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class ResolutionResultBuilder implements ResolvedConfigurationListener {

    private ResolvedConfigurationIdentifier root;
    private Map<ModuleVersionIdentifier, Map<Object, DefaultResolvedDependencyResult>> deps
            = new LinkedHashMap<ModuleVersionIdentifier, Map<Object, DefaultResolvedDependencyResult>>();

    public void start(ResolvedConfigurationIdentifier root) {
        this.root = root;
    }

    public void resolvedConfiguration(ResolvedConfigurationIdentifier id, Collection<DefaultResolvedDependencyResult> dependencies) {
        if (!deps.containsKey(id.getId())) {
            deps.put(id.getId(), new LinkedHashMap<Object, DefaultResolvedDependencyResult>());
        }

        //The configurations are merged into the dependencies that have the same selected+requested
        Map<Object, DefaultResolvedDependencyResult> accumulatedDependencies = deps.get(id.getId());
        for (DefaultResolvedDependencyResult d : dependencies) {
            if (accumulatedDependencies.containsKey(d.getSelectionId())) {
                accumulatedDependencies.get(d.getSelectionId()).appendConfigurations(d.getSelectedConfigurations());
            } else {
                accumulatedDependencies.put(d.getSelectionId(), d);
            }
        }
    }

    public ResolutionResult getResult() {
        return new DefaultResolutionResult(buildGraph());
    }

    private DefaultResolvedModuleVersionResult buildGraph() {
        Map<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult> visited = new HashMap<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult>();
        DefaultResolvedDependencyResult id = new DefaultResolvedDependencyResult(newSelector(root.getId()), root.getId(), newHashSet(root.getConfiguration()));
        return buildNode(id, visited);
    }

    private DefaultResolvedModuleVersionResult buildNode(DefaultResolvedDependencyResult id, Map<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult> visited) {
        if (visited.containsKey(id)) {
            return visited.get(id);
        }
        DefaultResolvedModuleVersionResult node = id.getSelected();
        visited.put(id, node);

        Map<Object, DefaultResolvedDependencyResult> theDeps = this.deps.get(id.getSelected().getId());
        if (theDeps == null) {
            //does not have any dependencies, return.
            return node;
        }
        for (DefaultResolvedDependencyResult sel : theDeps.values()) {
            //recursively feed with the dependencies
            buildNode(sel, visited);
            //add dependency and the dependee
            node.linkDependency(sel);
        }

        return node;
    }
}
