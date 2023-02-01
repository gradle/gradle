/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@ServiceScope(Scopes.BuildSession.class)
public class ArtifactSetToFileCollectionFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final TaskDependencyFactory taskDependencyFactory;

    public ArtifactSetToFileCollectionFactory(TaskDependencyFactory taskDependencyFactory, BuildOperationExecutor buildOperationExecutor) {
        this.taskDependencyFactory = taskDependencyFactory;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    /**
     * Presents the contents of the given artifacts as a partial {@link FileCollectionInternal} implementation.
     *
     * <p>This produces only a minimal implementation to use for artifact sets loaded from the configuration cache
     * Over time, this should be merged with the FileCollection implementation in DefaultConfiguration
     */
    public FileCollectionInternal asFileCollection(ResolvedArtifactSet artifacts) {
        // TODO - merge these file collections for all transforms so that non-scheduled transforms are executed in parallel
        return new ResolutionBackedFileCollection(
            new ResolutionResultProvider<SelectedArtifactSet>() {
                @Override
                public SelectedArtifactSet getTaskDependencyValue() {
                    throw new IllegalStateException();
                }

                @Override
                public SelectedArtifactSet getValue() {
                    return new SelectedArtifactSet() {
                        @Override
                        public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
                            ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(visitor);
                        }

                        @Override
                        public void visitDependencies(TaskDependencyResolveContext context) {
                            throw new IllegalStateException();
                        }
                    };
                }
            },
            false,
            new ResolutionHost() {
                @Override
                public DisplayName displayName(String type) {
                    return Describables.of("unknown configuration");
                }

                @Override
                public Optional<? extends RuntimeException> mapFailure(String type, Collection<Throwable> failures) {
                    if (failures.isEmpty()) {
                        return Optional.empty();
                    } else {
                        return Optional.of(new DefaultLenientConfiguration.ArtifactResolveException(type, "??", displayName(type).getDisplayName(), failures));
                    }
                }
            }, taskDependencyFactory
        );
    }

    /**
     * Presents the contents of the given artifacts as a supplier of {@link ResolvedArtifactResult} instances.
     *
     * <p>Over time, this should be merged with the ArtifactCollection implementation in DefaultConfiguration
     */
    public Set<ResolvedArtifactResult> asResolvedArtifacts(ResolvedArtifactSet artifacts) {
        ResolvedArtifactCollectingVisitor collectingVisitor = new ResolvedArtifactCollectingVisitor();
        ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(collectingVisitor);
        if (!collectingVisitor.getFailures().isEmpty()) {
            throw UncheckedException.throwAsUncheckedException(collectingVisitor.getFailures().iterator().next());
        }
        return collectingVisitor.getArtifacts();
    }
}
