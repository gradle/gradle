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

import groovy.lang.Closure;
import org.gradle.api.Action;
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
        final Set<DependencyResult> out = new LinkedHashSet<DependencyResult>();
        allDependencies(new Action<DependencyResult>() {
            public void execute(DependencyResult dep) {
                out.add(dep);
            }
        });
        return out;
    }

    public void allDependencies(Action<DependencyResult> action) {
        Set<ResolvedModuleVersionResult> visited = new LinkedHashSet<ResolvedModuleVersionResult>();
        eachDependency(root, action, visited);
    }

    public void allDependencies(final Closure closure) {
        allDependencies(new Action<DependencyResult>() {
            public void execute(DependencyResult dependencyResult) {
                closure.call(dependencyResult);
            }
        });
    }

    private void eachDependency(ResolvedModuleVersionResult node, Action<DependencyResult> action, Set<ResolvedModuleVersionResult> visited) {
        if (!visited.add(node)) {
            return;
        }
        for (DependencyResult d : node.getDependencies()) {
            if (d instanceof ResolvedDependencyResult) {
                eachDependency(((ResolvedDependencyResult) d).getSelected(), action, visited);
            }
            action.execute(d);
        }
    }
}
