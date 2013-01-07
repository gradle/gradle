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
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;

import java.util.LinkedHashSet;
import java.util.Set;

public class RequestedVersion extends AbstractRenderableDependencyResult {
    private final ModuleVersionSelector requested;
    private final ModuleVersionIdentifier actual;
    private final boolean resolvable;
    private final String description;
    private final Set<RenderableDependency> children = new LinkedHashSet<RenderableDependency>();

    public RequestedVersion(ModuleVersionIdentifier actual, boolean resolvable, String description) {
        this(DefaultModuleVersionSelector.newSelector(actual.getGroup(), actual.getName(), actual.getVersion()), actual, resolvable, description);
    }

    public RequestedVersion(ModuleVersionSelector requested, ModuleVersionIdentifier actual, boolean resolvable, String description) {
        this.requested = requested;
        this.actual = actual;
        this.resolvable = resolvable;
        this.description = description;
    }

    public void addChild(InvertedRenderableDependency child) {
        children.addAll(child.getChildren());
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    protected ModuleVersionIdentifier getActual() {
        return actual;
    }

    @Override
    protected ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public boolean isResolvable() {
        return resolvable;
    }

    public Set<RenderableDependency> getChildren() {
        return children;
    }
}
