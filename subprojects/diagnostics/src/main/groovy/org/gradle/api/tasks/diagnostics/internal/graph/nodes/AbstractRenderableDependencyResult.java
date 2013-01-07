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

public abstract class AbstractRenderableDependencyResult implements RenderableDependency {
    public ModuleVersionIdentifier getId() {
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
        ModuleVersionSelector requested = getRequested();
        return requested.getGroup() + ":" + requested.getName() + ":" + requested.getVersion();
    }

    private String getVerboseName() {
        ModuleVersionSelector requested = getRequested();
        ModuleVersionIdentifier selected = getActual();
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

    protected abstract ModuleVersionSelector getRequested();

    protected abstract ModuleVersionIdentifier getActual();
}
