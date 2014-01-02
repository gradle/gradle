/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.tasks.diagnostics.internal.insight.DescribableComponentSelectionReason;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DependencyReportHeader implements RenderableDependency {
    private final DependencyEdge dependency;
    private final Set<RenderableDependency> children = Collections.unmodifiableSet(new HashSet<RenderableDependency>());

    public DependencyReportHeader(DependencyEdge dependency) {
        this.dependency = dependency;
    }

    public ComponentIdentifier getId() {
        return dependency.getActual();
    }

    public String getName() {
        return getId().getDisplayName();
    }

    public String getDescription() {
        return new DescribableComponentSelectionReason(dependency.getReason()).describe();
    }

    public boolean isResolvable() {
        return dependency.isResolvable();
    }

    public Set<? extends RenderableDependency> getChildren() {
        return children;
    }
}
