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

    public Set<? extends ResolvedDependencyResult> getAllDependencies() {
        //TODO SF/AM make sure this works if there are cycles / coverage
        //void allDependencies(Action<ResolvedDependencyResult> action) / void allDependencies(Closure cl)
        Set<ResolvedDependencyResult> out = new LinkedHashSet<ResolvedDependencyResult>();
        collectDependencies(root, out);
        return out;
    }

    private void collectDependencies(ResolvedModuleVersionResult node, Set<ResolvedDependencyResult> out) {
        for (ResolvedDependencyResult d : node.getDependencies()) {
            collectDependencies(d.getSelected(), out);
            out.add(d);
        }
    }
}
