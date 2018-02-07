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
import org.gradle.api.artifacts.result.ResolvedVariantResult;

import java.util.Collections;
import java.util.Set;

import static org.gradle.api.tasks.diagnostics.internal.graph.nodes.SelectionReasonHelper.getReasonDescription;

public class DependencyReportHeader implements RenderableDependency {
    private final DependencyEdge dependency;
    private final ResolvedVariantResult selectedVariant;

    public DependencyReportHeader(DependencyEdge dependency, ResolvedVariantResult extraDetails) {
        this.dependency = dependency;
        this.selectedVariant = extraDetails;
    }

    @Override
    public ComponentIdentifier getId() {
        return dependency.getActual();
    }

    @Override
    public String getName() {
        return getId().getDisplayName();
    }

    @Override
    public String getDescription() {
        return getReasonDescription(dependency.getReason());
    }

    @Override
    public ResolutionState getResolutionState() {
        return dependency.isResolvable() ? ResolutionState.RESOLVED : ResolutionState.FAILED;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return Collections.emptySet();
    }

    @Override
    public ResolvedVariantResult getResolvedVariant() {
        return selectedVariant;
    }
}
