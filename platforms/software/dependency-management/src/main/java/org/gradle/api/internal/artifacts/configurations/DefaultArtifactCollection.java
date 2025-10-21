/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class DefaultArtifactCollection implements ArtifactCollectionInternal {

    private final SelectedArtifactSet artifacts;
    private final boolean lenient;
    private final ResolutionHost resolutionHost;
    private final CalculatedValue<ArtifactSetResult> result;
    private final TaskDependencyFactory taskDependencyFactory;

    public DefaultArtifactCollection(
        SelectedArtifactSet artifacts,
        boolean lenient,
        ResolutionHost resolutionHost,
        TaskDependencyFactory taskDependencyFactory,
        CalculatedValueFactory calculatedValueFactory,
        AttributeDesugaring attributeDesugaring
    ) {
        this.artifacts = artifacts;
        this.lenient = lenient;
        this.resolutionHost = resolutionHost;
        this.taskDependencyFactory = taskDependencyFactory;

        this.result = calculatedValueFactory.create(resolutionHost.displayName("files"), () -> {
            ResolvedArtifactCollectingVisitor visitor = new ResolvedArtifactCollectingVisitor(attributeDesugaring);
            artifacts.visitArtifacts(visitor, lenient);

            Set<ResolvedArtifactResult> artifactResults = visitor.getArtifacts();
            Set<Throwable> failures = visitor.getFailures();

            if (!lenient) {
                resolutionHost.rethrowFailuresAndReportProblems("artifacts", failures);
            }
            return new ArtifactSetResult(artifactResults, failures);
        });
    }

    @Override
    public ResolutionHost getResolutionHost() {
        return resolutionHost;
    }

    @Override
    public boolean isLenient() {
        return lenient;
    }

    @Override
    public FileCollectionInternal getArtifactFiles() {
        return new ResolutionBackedFileCollection(
            artifacts,
            lenient,
            resolutionHost,
            taskDependencyFactory
        );
    }

    @Override
    public Set<ResolvedArtifactResult> getArtifacts() {
        ensureResolved();
        return result.get().artifactResults;
    }

    @Override
    public Provider<Set<ResolvedArtifactResult>> getResolvedArtifacts() {
        return new BuildableBackedProvider<>(getArtifactFiles(), Cast.uncheckedCast(Set.class), new ArtifactCollectionResolvedArtifactsFactory(this));
    }

    @Override
    public Iterator<ResolvedArtifactResult> iterator() {
        ensureResolved();
        return result.get().artifactResults.iterator();
    }

    @Override
    public Collection<Throwable> getFailures() {
        ensureResolved();
        return result.get().failures;
    }

    @Override
    public void visitArtifacts(ArtifactVisitor visitor) {
        // TODO - if already resolved, use the results
        artifacts.visitArtifacts(visitor, lenient);
    }

    private void ensureResolved() {
        result.finalizeIfNotAlready();
    }

    private static class ArtifactSetResult {
        private final Set<ResolvedArtifactResult> artifactResults;
        private final Set<Throwable> failures;

        ArtifactSetResult(Set<ResolvedArtifactResult> artifactResults, Set<Throwable> failures) {
            this.artifactResults = artifactResults;
            this.failures = failures;
        }
    }

    private static class ArtifactCollectionResolvedArtifactsFactory implements Factory<Set<ResolvedArtifactResult>> {

        private final ArtifactCollection artifactCollection;

        private ArtifactCollectionResolvedArtifactsFactory(ArtifactCollection artifactCollection) {
            this.artifactCollection = artifactCollection;
        }

        @Override
        public Set<ResolvedArtifactResult> create() {
            return artifactCollection.getArtifacts();
        }
    }

}
