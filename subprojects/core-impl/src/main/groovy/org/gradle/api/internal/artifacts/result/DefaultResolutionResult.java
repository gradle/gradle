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
import org.gradle.api.internal.Actions;
import org.gradle.api.internal.ClosureBackedAction;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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

    public void allDependencies(Action<? super DependencyResult> action) {
        eachElement(root, Actions.doNothing(), action, new HashSet<ResolvedModuleVersionResult>());
    }

    public void allDependencies(final Closure closure) {
        allDependencies(new ClosureBackedAction<DependencyResult>(closure));
    }

    private void eachElement(ResolvedModuleVersionResult node,
                             Action<? super ResolvedModuleVersionResult> moduleAction, Action<? super DependencyResult> dependencyAction,
                             Set<ResolvedModuleVersionResult> visited) {
        if (!visited.add(node)) {
            return;
        }
        moduleAction.execute(node);
        for (DependencyResult d : node.getDependencies()) {
            dependencyAction.execute(d);
            if (d instanceof ResolvedDependencyResult) {
                eachElement(((ResolvedDependencyResult) d).getSelected(), moduleAction, dependencyAction, visited);
            }
        }
    }

    public Set<ResolvedModuleVersionResult> getAllModuleVersions() {
        final Set<ResolvedModuleVersionResult> out = new LinkedHashSet<ResolvedModuleVersionResult>();
        eachElement(root, Actions.doNothing(), Actions.doNothing(), out);
        return out;
    }

    public void allModuleVersions(final Action<? super ResolvedModuleVersionResult> action) {
        eachElement(root, action, Actions.doNothing(), new HashSet<ResolvedModuleVersionResult>());
    }

    public void allModuleVersions(final Closure closure) {
        allModuleVersions(new ClosureBackedAction<ResolvedModuleVersionResult>(closure));
    }

}
