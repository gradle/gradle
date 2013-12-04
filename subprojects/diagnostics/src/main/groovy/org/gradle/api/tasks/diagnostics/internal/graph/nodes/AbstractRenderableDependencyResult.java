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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

public abstract class AbstractRenderableDependencyResult implements RenderableDependency {
    public ModuleComponentIdentifier getId() {
        return getActual();
    }

    public String getName() {
        if (requestedEqualsSelected()) {
            return getSimpleName();
        }
        return getVerboseName();
    }

    public abstract boolean isResolvable();

    private boolean requestedEqualsSelected() {
        return getRequested().matchesStrictly(getActual());
    }

    @Nullable
    public String getDescription() {
        return null;
    }

    private String getSimpleName() {
        ComponentSelector requested = getRequested();
        return requested.getDisplayName();
    }

    private String getVerboseName() {
        ComponentSelector requested = getRequested();
        ModuleComponentIdentifier selected = getActual();

        if(requested instanceof ModuleComponentSelector) {
            ModuleComponentSelector requestedModuleComponentSelector = (ModuleComponentSelector)requested;
            if(!selected.getGroup().equals(requestedModuleComponentSelector.getGroup()) || !selected.getModule().equals(requestedModuleComponentSelector.getModule())) {
                return getSimpleName() + " -> " + selected.getGroup() + ":" + selected.getModule() + ":" + selected.getVersion();
            }
            if (!selected.getVersion().equals(requestedModuleComponentSelector.getVersion())) {
                return getSimpleName() + " -> " + selected.getVersion();
            }
        }

        return getSimpleName();
    }

    protected abstract ComponentSelector getRequested();

    protected abstract ModuleComponentIdentifier getActual();
}
