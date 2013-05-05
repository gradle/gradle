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

package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;

import java.util.Collections;
import java.util.Set;

public class UnresolvedDependencyEdge implements DependencyEdge {
    private final UnresolvedDependencyResult dependency;
    private final ModuleVersionIdentifier actual;

    public UnresolvedDependencyEdge(UnresolvedDependencyResult dependency) {
        this.dependency = dependency;
        ModuleVersionSelector attempted = dependency.getAttempted();
        actual = DefaultModuleVersionIdentifier.newId(attempted.getGroup(), attempted.getName(), attempted.getVersion());
    }

    public boolean isResolvable() {
        return false;
    }

    public ModuleVersionSelector getRequested() {
        return dependency.getRequested();
    }

    public ModuleVersionIdentifier getActual() {
        return actual;
    }

    public ModuleVersionSelectionReason getReason() {
        return dependency.getAttemptedReason();
    }

    public ModuleVersionIdentifier getFrom() {
        return dependency.getFrom().getId();
    }

    public Set<? extends RenderableDependency> getChildren() {
        return Collections.singleton(new InvertedRenderableModuleResult(dependency.getFrom()));
    }
}
