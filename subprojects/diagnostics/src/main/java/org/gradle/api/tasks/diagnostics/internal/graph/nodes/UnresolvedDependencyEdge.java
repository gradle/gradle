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

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;

import java.util.Collections;
import java.util.Set;

public class UnresolvedDependencyEdge implements DependencyEdge {
    private final UnresolvedDependencyResult dependency;
    private final ModuleComponentIdentifier actual;

    public UnresolvedDependencyEdge(UnresolvedDependencyResult dependency) {
        this.dependency = dependency;
        // TODO:Prezi Is this cast safe? Can't this be a LibraryComponentSelector, say?
        ModuleComponentSelector attempted = (ModuleComponentSelector)dependency.getAttempted();
        actual = DefaultModuleComponentIdentifier.newId(attempted.getGroup(), attempted.getModule(), attempted.getVersionConstraint().getPreferredVersion());
    }

    @Override
    public boolean isResolvable() {
        return false;
    }

    @Override
    public ComponentSelector getRequested() {
        return dependency.getRequested();
    }

    @Override
    public ModuleComponentIdentifier getActual() {
        return actual;
    }

    @Override
    public ComponentSelectionReason getReason() {
        return dependency.getAttemptedReason();
    }

    @Override
    public ModuleComponentIdentifier getFrom() {
        return (ModuleComponentIdentifier)dependency.getFrom().getId();
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return Collections.singleton(new InvertedRenderableModuleResult(dependency.getFrom()));
    }
}
