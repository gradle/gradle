/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.reporting.dependents.internal;

import com.google.common.collect.Sets;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.util.Set;

public class ComponentRenderableDependency implements RenderableDependency {

    private final ComponentSpec component;
    private final ComponentSpecInternal componentInternal;
    private final Set<ComponentRenderableDependency> children = Sets.newLinkedHashSet();

    public ComponentRenderableDependency(ComponentSpec component) {
        this.component = component;
        this.componentInternal = (ComponentSpecInternal) component;
    }

    @Override
    public Object getId() {
        return componentInternal.getIdentifier();
    }

    @Override
    public String getName() {
        return componentInternal.getIdentifier().getProjectScopedName();
    }

    @Override
    public String getDescription() {
        return component.getDisplayName();
    }

    @Override
    public ResolutionState getResolutionState() {
        return ResolutionState.RESOLVED;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return children;
    }
}
