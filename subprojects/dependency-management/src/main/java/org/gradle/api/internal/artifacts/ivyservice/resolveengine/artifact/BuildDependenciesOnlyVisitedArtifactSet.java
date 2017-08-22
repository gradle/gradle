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
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;

import java.util.Set;

public class BuildDependenciesOnlyVisitedArtifactSet implements VisitedArtifactSet {
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final VisitedArtifactsResults artifactsResults;
    private final ArtifactTransforms artifactTransforms;

    public BuildDependenciesOnlyVisitedArtifactSet(Set<UnresolvedDependency> unresolvedDependencies, VisitedArtifactsResults artifactsResults, ArtifactTransforms artifactTransforms) {
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifactsResults = artifactsResults;
        this.artifactTransforms = artifactTransforms;
    }

    @Override
    public SelectedArtifactSet select(Spec<? super Dependency> dependencySpec, AttributeContainerInternal requestedAttributes, Spec<? super ComponentIdentifier> componentSpec, boolean allowNoMatchingVariant) {
        VariantSelector variantSelector = artifactTransforms.variantSelector(requestedAttributes, allowNoMatchingVariant);
        ResolvedArtifactSet selectedArtifacts = artifactsResults.select(componentSpec, variantSelector).getArtifacts();
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
        public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
            for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                visitor.visitFailure(unresolvedDependency.getProblem());
            }
            selectedArtifacts.collectBuildDependencies(visitor);
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
            throw new UnsupportedOperationException("Artifacts have not been resolved.");
        }
    }
}
