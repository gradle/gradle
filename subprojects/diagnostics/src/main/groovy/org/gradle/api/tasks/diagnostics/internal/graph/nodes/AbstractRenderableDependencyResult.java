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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

public abstract class AbstractRenderableDependencyResult implements RenderableDependency {
    protected final DependencyResult dependency;
    protected final String description;

    public AbstractRenderableDependencyResult(DependencyResult dependency, @Nullable String description) {
        this.dependency = dependency;
        this.description = description;
    }

    public ModuleVersionIdentifier getId() {
        return dependency.getSelected().getId();
    }

    public String getName() {
        if (requestedEqualsSelected()) {
            return getSimpleName();
        }
        return getVerboseName();
    }

    public boolean isResolvable() {
        return dependency instanceof ResolvedDependencyResult;
    }

    private boolean requestedEqualsSelected() {
        return dependency.getSelected() == null || dependency.getRequested().matchesStrictly(dependency.getSelected().getId());
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    private String getSimpleName() {
        return dependency.getRequested().getGroup() + ":" + dependency.getRequested().getName() + ":" + dependency.getRequested().getVersion();
    }

    private String getVerboseName() {
        ModuleVersionSelector requested = dependency.getRequested();
        ModuleVersionIdentifier selected = dependency.getSelected().getId();
        if(!selected.getGroup().equals(requested.getGroup())) {
            return getSimpleName() + " -> " + selected.getGroup() + ":" + selected.getName() + ":" + selected.getVersion();
        }
        if (!selected.getName().equals(requested.getName())) {
            return getSimpleName() + " -> " + selected.getName() + ":" + selected.getVersion();
        }
        if (!selected.getVersion().equals(requested.getVersion())) {
            return getSimpleName() + " -> " + selected.getVersion();
        }
        return getSimpleName();
    }

    @Override
    public String toString() {
        return dependency.toString();
    }
}
