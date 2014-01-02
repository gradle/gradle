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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.component.DefaultProjectComponentIdentifier;

import java.util.Collections;
import java.util.Set;

public class RenderableUnresolvedDependencyResult implements RenderableDependency {
    private final UnresolvedDependencyResult dependency;

    public RenderableUnresolvedDependencyResult(UnresolvedDependencyResult dependency) {
        this.dependency = dependency;
    }

    public boolean isResolvable() {
        return false;
    }

    protected ComponentSelector getRequested() {
        return dependency.getRequested();
    }

    protected ComponentIdentifier getActual() {
        return null;
    }

    public Set<RenderableDependency> getChildren() {
        return Collections.emptySet();
    }

    public ComponentIdentifier getId() {
        ComponentSelector attempted = dependency.getAttempted();

        if(attempted instanceof ProjectComponentSelector) {
            return DefaultProjectComponentIdentifier.newId(((ProjectComponentSelector) attempted).getProjectPath());
        }

        ModuleComponentSelector moduleComponentSelector = (ModuleComponentSelector)attempted;
        return DefaultModuleComponentIdentifier.newId(moduleComponentSelector.getGroup(), moduleComponentSelector.getModule(), moduleComponentSelector.getVersion());
    }

    public String getDescription() {
        return null;
    }

    public String getName() {
        ComponentSelector requested = dependency.getRequested();
        ComponentSelector attempted = dependency.getAttempted();

        if(requested.equals(attempted)) {
            return requested.getDisplayName();
        }

        if(requested instanceof ModuleComponentSelector && attempted instanceof ModuleComponentSelector) {
            ModuleComponentSelector requestedSelector = (ModuleComponentSelector)requested;
            ModuleComponentSelector attemptedSelector = (ModuleComponentSelector)attempted;

            if(requestedSelector.getGroup().equals(attemptedSelector.getGroup())
               && requestedSelector.getModule().equals(attemptedSelector.getModule())
               && !requestedSelector.getVersion().equals(attemptedSelector.getVersion())) {
                return requested.getDisplayName() + " -> " + ((ModuleComponentSelector) attempted).getVersion();
            }
        }

        return requested.getDisplayName() + " -> " + attempted.getDisplayName();
    }
}
