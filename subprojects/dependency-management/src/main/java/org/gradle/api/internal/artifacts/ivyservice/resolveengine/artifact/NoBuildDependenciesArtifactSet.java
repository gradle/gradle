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

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;

public class NoBuildDependenciesArtifactSet implements ArtifactSet {
    private final ArtifactSet set;

    public NoBuildDependenciesArtifactSet(ArtifactSet set) {
        this.set = set;
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        final ResolvedArtifactSet selectedArtifacts = set.select(componentFilter, selector);
        if (selectedArtifacts == ResolvedArtifactSet.EMPTY) {
            return selectedArtifacts;
        }
        return new NoDepsResolvedArtifactSet(selectedArtifacts);
    }

    private static class NoDepsResolvedArtifactSet implements ResolvedArtifactSet {
        private final ResolvedArtifactSet selectedArtifacts;

        public NoDepsResolvedArtifactSet(ResolvedArtifactSet selectedArtifacts) {
            this.selectedArtifacts = selectedArtifacts;
        }

        @Override
        public void visit(Visitor visitor) {
            selectedArtifacts.visit(visitor);
        }

        @Override
        public void visitTransformSources(TransformSourceVisitor visitor) {
            selectedArtifacts.visitTransformSources(visitor);
        }

        @Override
        public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
            selectedArtifacts.visitExternalArtifacts(visitor);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }
    }
}
