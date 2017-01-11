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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Collection;

public class BuildDependenciesOnlyVisitedArtifactSet implements VisitedArtifactSet {
    private final VisitedArtifactsResults artifactsResults;
    private final VisitedFileDependencyResults fileDependencyResults;
    private final ArtifactTransforms artifactTransforms;

    public BuildDependenciesOnlyVisitedArtifactSet(VisitedArtifactsResults artifactsResults, VisitedFileDependencyResults fileDependencyResults, ArtifactTransforms artifactTransforms) {
        this.artifactsResults = artifactsResults;
        this.fileDependencyResults = fileDependencyResults;
        this.artifactTransforms = artifactTransforms;
    }

    @Override
    public SelectedArtifactSet select(Spec<? super Dependency> dependencySpec, AttributeContainerInternal requestedAttributes, Spec<? super ComponentIdentifier> componentSpec) {
        Transformer<HasAttributes, Collection<? extends HasAttributes>> variantSelector = artifactTransforms.variantSelector(requestedAttributes);
        ResolvedArtifactSet selectedArtifacts = artifactsResults.select(componentSpec, variantSelector).getArtifacts();
        ResolvedArtifactSet selectedFiles = fileDependencyResults.select(variantSelector).getFiles();
        return new BuildDependenciesOnlySelectedArtifactSet(selectedArtifacts, selectedFiles);
    }

    private static class BuildDependenciesOnlySelectedArtifactSet implements SelectedArtifactSet {
        private final ResolvedArtifactSet selectedArtifacts;
        private final ResolvedArtifactSet selectedFiles;

        BuildDependenciesOnlySelectedArtifactSet(ResolvedArtifactSet selectedArtifacts, ResolvedArtifactSet selectedFiles) {
            this.selectedArtifacts = selectedArtifacts;
            this.selectedFiles = selectedFiles;
        }

        @Override
        public <T extends Collection<Object>> T collectBuildDependencies(T dest) {
            selectedArtifacts.collectBuildDependencies(dest);
            selectedFiles.collectBuildDependencies(dest);
            return dest;
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor) {
            throw new UnsupportedOperationException("Artifacts have not been resolved.");
        }

        @Override
        public <T extends Collection<? super ResolvedArtifactResult>> T collectArtifacts(T dest) throws ResolveException {
            throw new UnsupportedOperationException("Artifacts have not been resolved.");
        }

        @Override
        public <T extends Collection<? super File>> T collectFiles(T dest) throws ResolveException {
            throw new UnsupportedOperationException("Artifacts have not been resolved.");
        }
    }
}
