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
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Children of this renderable dependency node are its dependents.
 *
 * by Szczepan Faber, created at: 7/27/12
 */
public class InvertedRenderableDependencyResult extends AbstractRenderableDependencyResult {
    private final DependencyResult dependency;
    private final String description;

    public InvertedRenderableDependencyResult(DependencyResult dependency, String description) {
        this.dependency = dependency;
        this.description = description;
    }

    @Override
    public boolean isResolvable() {
        return dependency instanceof ResolvedDependencyResult;
    }

    @Override
    protected ModuleVersionSelector getRequested() {
        return dependency.getRequested();
    }

    @Override
    protected ModuleVersionIdentifier getActual() {
        if (dependency instanceof UnresolvedDependencyResult) {
            ModuleVersionSelector attempted = ((UnresolvedDependencyResult) dependency).getAttempted();
            return DefaultModuleVersionIdentifier.newId(attempted.getGroup(), attempted.getName(), attempted.getVersion());
        }
        return dependency.getSelected().getId();
    }

    @Override
    public String getDescription() {
        return description;
    }

    public Set<RenderableDependency> getChildren() {
        Set<RenderableDependency> out = new LinkedHashSet<RenderableDependency>();
        for (DependencyResult d : dependency.getSelected().getDependents()) {
            //we want only the dependents that match the requested
            if (d.getRequested().equals(dependency.getRequested())) {
                out.add(new InvertedRenderableModuleResult(d.getFrom()));
            }
        }

        return out;
    }
}
