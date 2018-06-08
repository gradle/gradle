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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId;

public class SimpleDependency extends AbstractRenderableDependency {

    private final ModuleComponentIdentifier id;
    private final String name;
    private final boolean resolvable;
    private final String description;
    private final Set<RenderableDependency> children = new LinkedHashSet<RenderableDependency>();

    public SimpleDependency(String name) {
        this(name, true, null);
    }

    public SimpleDependency(String name, boolean resolvable, String description) {
        this.name = name;
        this.resolvable = resolvable;
        this.description = description;
        this.id = newId(DefaultModuleIdentifier.newId(name, name), "1.0");
    }

    public ModuleComponentIdentifier getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ResolutionState getResolutionState() {
        return resolvable ? ResolutionState.RESOLVED : ResolutionState.FAILED;
    }

    public String getDescription() {
        return description;
    }

    public Set<RenderableDependency> getChildren() {
        return children;
    }
}
