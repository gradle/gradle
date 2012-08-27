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
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult;

import java.util.*;

/**
 * by Szczepan Faber, created at: 7/26/12
 */
public class ResolutionResultBuilder implements ResolvedConfigurationListener {

    private ResolvedConfigurationIdentifier root;

    private Map<ModuleVersionIdentifier, Set<DependencyResult>> deps
            = new LinkedHashMap<ModuleVersionIdentifier, Set<DependencyResult>>();

    public void start(ResolvedConfigurationIdentifier root) {
        this.root = root;
    }

    public void resolvedConfiguration(ResolvedConfigurationIdentifier id, Collection<InternalDependencyResult> dependencies) {
        if (!deps.containsKey(id.getId())) {
            deps.put(id.getId(), new LinkedHashSet<DependencyResult>());
        }

        Set<DependencyResult> accumulated = deps.get(id.getId());
        for (InternalDependencyResult d : dependencies) {
            if (d.getFailure() != null) {
                accumulated.add(new DefaultUnresolvedDependencyResult(d.getRequested(), d.getFailure()));
            } else {
                accumulated.add(new DefaultResolvedDependencyResult(d.getRequested(), d.getSelected()));
            }
        }
    }

    public ResolutionResult getResult() {
        return new DefaultResolutionResult(buildGraph());
    }

    private ResolvedModuleVersionResult buildGraph() {
        DefaultResolvedDependencyResult rootDependency = new DefaultResolvedDependencyResult(
                DefaultModuleVersionSelector.newSelector(root.getId()), root.getId());

        Set<ResolvedModuleVersionResult> visited = new HashSet<ResolvedModuleVersionResult>();

        linkDependencies(rootDependency, visited);

        return rootDependency.getSelected();
    }

    private void linkDependencies(DefaultResolvedDependencyResult dependency, Set<ResolvedModuleVersionResult> visited) {
        if (!visited.add(dependency.getSelected())) {
            return;
        }

        Set<DependencyResult> theDeps = deps.get(dependency.getSelected().getId());
        for (DependencyResult d: theDeps) {
            if (d instanceof DefaultResolvedDependencyResult) {
                DefaultResolvedDependencyResult resolved = (DefaultResolvedDependencyResult) d;
                linkDependencies(resolved, visited);
                resolved.getSelected().addDependee(dependency);
            }
            dependency.getSelected().addDependency(d);
        }
    }
}
