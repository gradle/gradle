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

import java.util.*;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class ResolutionResultBuilder implements ResolvedConfigurationListener {

    private ResolvedConfigurationIdentifier root;
    private Map<ModuleVersionIdentifier, Set<DefaultResolvedDependencyResult>> deps
            = new LinkedHashMap<ModuleVersionIdentifier, Set<DefaultResolvedDependencyResult>>();

    public void start(ResolvedConfigurationIdentifier root) {
        this.root = root;
    }

    public void resolvedConfiguration(ResolvedConfigurationIdentifier id, Collection<DefaultResolvedDependencyResult> dependencies) {
        //TODO SF add some unit test coverage
        if (!deps.containsKey(id.getId())) {
            deps.put(id.getId(), new LinkedHashSet<DefaultResolvedDependencyResult>(dependencies));
        } else {
            deps.get(id.getId()).addAll(dependencies);
        }
    }

    public ResolutionResult getResult() {
        return new DefaultResolutionResult(buildGraph());
    }

    private DefaultResolvedModuleVersionResult buildGraph() {
        Map<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult> visited = new HashMap<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult>();
        DefaultResolvedDependencyResult id = new DefaultResolvedDependencyResult(newSelector(root.getId()), root.getId());
        return buildNode(id, visited);
    }

    private DefaultResolvedModuleVersionResult buildNode(DefaultResolvedDependencyResult id, Map<DefaultResolvedDependencyResult, DefaultResolvedModuleVersionResult> visited) {
        if (visited.containsKey(id)) {
            return visited.get(id);
        }
        DefaultResolvedModuleVersionResult node = id.getSelected();
        visited.put(id, node);

        Set<DefaultResolvedDependencyResult> theDeps = this.deps.get(id.getSelected().getId());
        if (theDeps == null) {
            //does not have any dependencies, return.
            return node;
        }
        for (DefaultResolvedDependencyResult d : theDeps) {
            //recursively feed with the dependencies
            buildNode(d, visited);
            //add dependency and the dependee
            node.linkDependency(d);
        }

        return node;
    }
}
