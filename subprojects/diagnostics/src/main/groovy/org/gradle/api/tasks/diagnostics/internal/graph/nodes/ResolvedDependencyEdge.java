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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

import java.util.Collections;
import java.util.Set;

public class ResolvedDependencyEdge implements DependencyEdge {
    private final ResolvedDependencyResult dependency;

    public ResolvedDependencyEdge(ResolvedDependencyResult dependency) {
        this.dependency = dependency;
    }

    public boolean isResolvable() {
        return true;
    }

    public ComponentSelector getRequested() {
        return dependency.getRequested();
    }

    public ComponentSelectionReason getReason() {
        return dependency.getSelected().getSelectionReason();
    }

    public ComponentIdentifier getActual() {
        return dependency.getSelected().getId();
    }

    public ComponentIdentifier getFrom() {
        return dependency.getFrom().getId();
    }

    public Set<? extends RenderableDependency> getChildren() {
        return Collections.singleton(new InvertedRenderableModuleResult(dependency.getFrom()));
    }
}
