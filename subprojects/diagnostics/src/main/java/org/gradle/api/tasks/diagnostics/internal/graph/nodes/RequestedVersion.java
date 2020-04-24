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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.LinkedHashSet;
import java.util.Set;

public class RequestedVersion extends AbstractRenderableDependencyResult implements HasAttributes {
    private final ComponentSelector requested;
    private final ComponentIdentifier actual;
    private final boolean resolvable;
    private final Set<RenderableDependency> children = new LinkedHashSet<>();

    public RequestedVersion(ComponentSelector requested, ComponentIdentifier actual, boolean resolvable) {
        this.requested = requested;
        this.actual = actual;
        this.resolvable = resolvable;
    }

    public void addChild(DependencyEdge child) {
        children.addAll(child.getChildren());
    }

    @Override
    protected ComponentIdentifier getActual() {
        return actual;
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public ResolutionState getResolutionState() {
        return resolvable ? ResolutionState.RESOLVED : ResolutionState.FAILED;
    }

    @Override
    public Set<RenderableDependency> getChildren() {
        return children;
    }

    @Override
    public AttributeContainer getAttributes() {
        return requested instanceof ModuleComponentSelector
            ? requested.getAttributes()
            : ImmutableAttributes.EMPTY;
    }
}
