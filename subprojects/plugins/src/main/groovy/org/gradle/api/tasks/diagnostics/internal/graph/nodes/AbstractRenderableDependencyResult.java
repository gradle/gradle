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
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

import java.util.Set;

/**
 * by Szczepan Faber, created at: 7/27/12
 */
public abstract class AbstractRenderableDependencyResult implements RenderableDependency {

    protected final ResolvedDependencyResult dependency;
    protected final String description;

    public AbstractRenderableDependencyResult(ResolvedDependencyResult dependency, String description) {
        this.dependency = dependency;
        this.description = description;
    }

    public String getName() {
        if (!requestedEqualsSelected(dependency)) {
            return getVerboseName();
        } else {
            return requested();
        }
    }

    private String getVerboseName() {
        ModuleVersionSelector requested = dependency.getRequested();
        ModuleVersionIdentifier selected = dependency.getSelected().getId();
        if(!selected.getGroup().equals(requested.getGroup())) {
            return requested() + " -> " + selected.getGroup() + ":" + selected.getName() + ":" + selected.getVersion();
        } else if (!selected.getName().equals(requested.getName())) {
            return requested() + " -> " + selected.getName() + ":" + selected.getVersion();
        } else if (!selected.getVersion().equals(requested.getVersion())) {
            return requested() + " -> " + selected.getVersion();
        } else {
            return requested();
        }
    }

    private static boolean requestedEqualsSelected(ResolvedDependencyResult dependency) {
        return dependency.getRequested().getAsSpec().isSatisfiedBy(dependency.getSelected().getId());
    }

    private String requested() {
        return dependency.getRequested().getGroup() + ":" + dependency.getRequested().getName() + ":" + dependency.getRequested().getVersion();
    }

    public ModuleVersionIdentifier getId() {
        return dependency.getSelected().getId();
    }

    public String getDescription() {
        return description;
    }

    public abstract Set<RenderableDependency> getChildren();

    @Override
    public String toString() {
        return dependency.toString();
    }
}
