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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

public abstract class AbstractRenderableDependencyResult implements RenderableDependency {
    public ComponentIdentifier getId() {
        return getActual();
    }

    public String getName() {
        ComponentSelector requested = getRequested();
        ComponentIdentifier selected = getActual();

        if(requested.matchesStrictly(selected)) {
            return getSimpleName();
        }

        if(requested instanceof ModuleComponentSelector && selected instanceof ModuleComponentIdentifier) {
            ModuleComponentSelector requestedModuleComponentSelector = (ModuleComponentSelector)requested;
            ModuleComponentIdentifier selectedModuleComponentedIdentifier = (ModuleComponentIdentifier)selected;

            if(isSameGroupAndModuleButDifferentVersion(requestedModuleComponentSelector, selectedModuleComponentedIdentifier)) {
                return getSimpleName() + " -> " + selectedModuleComponentedIdentifier.getVersion();
            }
        }

        return getSimpleName() + " -> " + selected.getDisplayName();
    }

    /**
     * Checks if requested and selected module component differ by version.
     *
     * @param requested Requested module component selector
     * @param selected Selected module component identifier
     * @return Indicates whether version differs
     */
    private boolean isSameGroupAndModuleButDifferentVersion(ModuleComponentSelector requested, ModuleComponentIdentifier selected) {
        return requested.getGroup().equals(selected.getGroup()) && requested.getModule().equals(selected.getModule()) && !requested.getVersion().equals(selected.getVersion());
    }

    /**
     * Gets simple name of requested component selector.
     *
     * @return Display name of requested component selector
     */
    private String getSimpleName() {
        return getRequested().getDisplayName();
    }

    @Nullable
    public String getDescription() {
        return null;
    }

    protected abstract ComponentSelector getRequested();

    protected abstract ComponentIdentifier getActual();
}
