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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 8/10/12
 */
public class DefaultResolutionResult implements ResolutionResult {

    private final ResolvedModuleVersionResult root;

    public DefaultResolutionResult(ResolvedModuleVersionResult root) {
        assert root != null;
        this.root = root;
    }

    public ResolvedModuleVersionResult getRoot() {
        return root;
    }

    public Set<? extends DependencyResult> getAllDependencies() {
        //TODO SF api change: void allDependencies(Action<ResolvedDependencyResult> action) / void allDependencies(Closure cl)
        Set<DependencyResult> out = new LinkedHashSet<DependencyResult>();
        Set<ResolvedModuleVersionResult> visited = new LinkedHashSet<ResolvedModuleVersionResult>();
        collectDependencies(root, out, visited);
        return out;
    }

    private void collectDependencies(ResolvedModuleVersionResult node, Set<DependencyResult> out, Set<ResolvedModuleVersionResult> visited) {
        if (!visited.add(node)) {
            return;
        }
        for (DependencyResult d : node.getDependencies()) {
            if (d instanceof ResolvedDependencyResult) {
                collectDependencies(((ResolvedDependencyResult) d).getSelected(), out, visited);
            }
            out.add(d);
        }
    }
}
