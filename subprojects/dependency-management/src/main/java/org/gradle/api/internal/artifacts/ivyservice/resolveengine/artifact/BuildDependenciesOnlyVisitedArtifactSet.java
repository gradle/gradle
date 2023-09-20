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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.VariantSelectorFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;

import java.util.Set;

public class BuildDependenciesOnlyVisitedArtifactSet implements VisitedArtifactSet {
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final VisitedArtifactsResults artifactsResults;
    private final VariantSelectorFactory variantSelectorFactory;
    private final TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory;

    public BuildDependenciesOnlyVisitedArtifactSet(
        Set<UnresolvedDependency> unresolvedDependencies,
        VisitedArtifactsResults artifactsResults,
        VariantSelectorFactory variantSelectorFactory,
        TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory
    ) {
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifactsResults = artifactsResults;
        this.variantSelectorFactory = variantSelectorFactory;
        this.dependenciesResolverFactory = dependenciesResolverFactory;
    }

    @Override
    public SelectedArtifactSet select(Spec<? super Dependency> dependencySpec, AttributeContainerInternal requestedAttributes, Spec<? super ComponentIdentifier> componentSpec, boolean allowNoMatchingVariant, boolean selectFromAllVariants) {
        ArtifactVariantSelector variantSelector = variantSelectorFactory.create(dependenciesResolverFactory);
        ResolvedArtifactSet selectedArtifacts = artifactsResults.select(componentSpec, variantSelector, selectFromAllVariants, allowNoMatchingVariant, requestedAttributes.asImmutable()).getArtifacts();
        return new BuildDependenciesOnlySelectedArtifactSet(unresolvedDependencies, selectedArtifacts);
    }

    private static class BuildDependenciesOnlySelectedArtifactSet implements SelectedArtifactSet {
        private final Set<UnresolvedDependency> unresolvedDependencies;
        private final ResolvedArtifactSet selectedArtifacts;

        BuildDependenciesOnlySelectedArtifactSet(Set<UnresolvedDependency> unresolvedDependencies, ResolvedArtifactSet selectedArtifacts) {
            this.unresolvedDependencies = unresolvedDependencies;
            this.selectedArtifacts = selectedArtifacts;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                context.visitFailure(unresolvedDependency.getProblem());
            }
            context.add(selectedArtifacts);
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
            throw new UnsupportedOperationException("Artifacts have not been resolved.");
        }
    }
}
