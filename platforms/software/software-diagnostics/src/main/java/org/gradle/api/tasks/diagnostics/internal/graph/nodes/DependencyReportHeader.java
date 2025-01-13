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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class DependencyReportHeader extends AbstractRenderableDependency implements HasAttributes {
    private final DependencyEdge dependency;
    private final String description;
    private final List<ResolvedVariantResult> selectedVariants;
    private final List<ResolvedVariantResult> allVariants;
    private final List<Section> extraDetails;

    public DependencyReportHeader(DependencyEdge dependency, @Nullable String description, List<Section> extraDetails) {
        this.dependency = dependency;
        this.description = description;
        this.extraDetails = extraDetails;
        this.selectedVariants = dependency.getSelectedVariants();
        this.allVariants = dependency.getAllVariants();
    }

    @Nonnull
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
        return description;
    }

    @Override
    public ResolutionState getResolutionState() {
        return dependency.isResolvable() ? ResolutionState.RESOLVED : ResolutionState.FAILED;
    }

    @Override
    public List<ResolvedVariantResult> getResolvedVariants() {
        return selectedVariants;
    }

    @Override
    public List<ResolvedVariantResult> getAllVariants() {
        return allVariants;
    }

    @Override
    public AttributeContainer getAttributes() {
        ComponentSelector requested = dependency.getRequested();
        return requested instanceof ModuleComponentSelector
            ? requested.getAttributes()
            : ImmutableAttributes.EMPTY;
    }

    @Override
    public List<Section> getExtraDetails() {
        return extraDetails;
    }
}
