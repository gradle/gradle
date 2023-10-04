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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    public ResolutionHost resolutionHost(String displayName) {
        return new NameBackedResolutionHost(displayName);
    }

    /**
     * Presents the contents of the given artifacts as a partial {@link FileCollectionInternal} implementation.
     *
     * <p>This produces only a minimal implementation to use for artifact sets loaded from the configuration cache
     * Over time, this should be merged with the FileCollection implementation in DefaultConfiguration
     */
    public ResolutionBackedFileCollection asFileCollection(String displayName, boolean lenient, List<?> elements) {
        return new ResolutionBackedFileCollection(new PartialSelectedArtifactProvider(elements), lenient, new NameBackedResolutionHost(displayName), taskDependencyFactory);
    }

    public ResolvedArtifactSet asResolvedArtifactSet(Throwable failure) {
        return new BrokenResolvedArtifactSet(failure);
    }

    public ResolvedArtifactSet asResolvedArtifactSet(
        ComponentArtifactIdentifier id,
        AttributeContainer variantAttributes,
        List<? extends Capability> capabilities,
        DisplayName variantDisplayName,
        File file
    ) {
        return new ResolvedArtifactSet() {
            @Override
            public void visit(Visitor visitor) {
                visitor.visitArtifacts(new Artifacts() {
                    @Override
                    public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
                        // Nothing to do
                    }

                    @Override
                    public void visit(ArtifactVisitor visitor) {
                        if (visitor.prepareForVisit(FileCollectionInternal.OTHER) == FileCollectionStructureVisitor.VisitType.Visit) {
                            visitor.visitArtifact(variantDisplayName, variantAttributes, capabilities, new ResolvableArtifact() {
                                @Override
                                public ComponentArtifactIdentifier getId() {
                                    return id;
                                }

                                @Override
                                public boolean isResolveSynchronously() {
                                    return true;
                                }

                                @Override
                                public IvyArtifactName getArtifactName() {
                                    return DefaultIvyArtifactName.forFile(file, null);
                                }

                                @Override
                                public File getFile() {
                                    return file;
                                }

                                @Override
                                public CalculatedValue<File> getFileSource() {
                                    throw new UnsupportedOperationException();
                                }

                                @Override
                                public ResolvableArtifact transformedTo(File file) {
                                    throw new UnsupportedOperationException();
                                }

                                @Override
                                public ResolvedArtifact toPublicView() {
                                    throw new UnsupportedOperationException();
                                }

                                @Override
                                public void visitDependencies(TaskDependencyResolveContext context) {
                                }
                            });
                            visitor.endVisitCollection(FileCollectionInternal.OTHER);
                        }
                    }
                });
            }

            @Override
            public void visitTransformSources(TransformSourceVisitor visitor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Presents the contents of the given artifacts as a supplier of {@link ResolvedArtifactResult} instances.
     *
     * <p>Over time, this should be merged with the ArtifactCollection implementation in DefaultConfiguration
     */
    public Set<ResolvedArtifactResult> asResolvedArtifacts(ResolvedArtifactSet artifacts, boolean lenient) {
        ResolvedArtifactCollectingVisitor collectingVisitor = new ResolvedArtifactCollectingVisitor();
        ParallelResolveArtifactSet.wrap(artifacts, buildOperationExecutor).visit(collectingVisitor);
        if (!lenient && !collectingVisitor.getFailures().isEmpty()) {
            throw UncheckedException.throwAsUncheckedException(collectingVisitor.getFailures().iterator().next());
        }
        return collectingVisitor.getArtifacts();
    }

    private static class NameBackedResolutionHost implements ResolutionHost {
        private final String displayName;

        public NameBackedResolutionHost(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public DisplayName displayName(String type) {
            return Describables.of(getDisplayName(), type);
        }

        @Override
        public Optional<? extends RuntimeException> mapFailure(String type, Collection<Throwable> failures) {
            if (failures.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new DefaultLenientConfiguration.ArtifactResolveException(type, getDisplayName(), failures));
            }
        }
    }

    private static class FileBackedArtifactSet implements ResolvedArtifactSet {
        private final File file;

        public FileBackedArtifactSet(File file) {
            this.file = file;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.visitArtifacts(new Artifacts() {
                @Override
                public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
                    // Nothing to do
                }

                @Override
                public void visit(ArtifactVisitor visitor) {
                    if (visitor.prepareForVisit(FileCollectionInternal.OTHER) == FileCollectionStructureVisitor.VisitType.Visit) {
                        ((ArtifactVisitorToResolvedFileVisitorAdapter) visitor).visitFile(file);
                        visitor.endVisitCollection(FileCollectionInternal.OTHER);
                    }
                }
            });
        }

        @Override
        public void visitTransformSources(TransformSourceVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            throw new UnsupportedOperationException();
        }
    }

    // "partial" in the sense that some artifacts are only available as a File, and have no metadata
    private static class PartialSelectedArtifactSet implements SelectedArtifactSet {
        private final List<?> elements;
        private final BuildOperationExecutor buildOperationExecutor;

        public PartialSelectedArtifactSet(List<?> elements, BuildOperationExecutor buildOperationExecutor) {
            this.elements = elements;
            this.buildOperationExecutor = buildOperationExecutor;
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
            List<ResolvedArtifactSet> artifactSets = new ArrayList<>();
            for (Object element : elements) {
                if (element instanceof ResolvedArtifactSet) {
                    artifactSets.add((ResolvedArtifactSet) element);
                } else {
                    // Should not be used, and cannot be provided as the artifact metadata may have been discarded.
                    throw new UnsupportedOperationException();
                }
            }
            ParallelResolveArtifactSet.wrap(CompositeResolvedArtifactSet.of(artifactSets), buildOperationExecutor).visit(visitor);
        }

        @Override
        public void visitFiles(ResolvedFileVisitor visitor, boolean continueOnSelectionFailure) {
            List<ResolvedArtifactSet> artifactSets = new ArrayList<>();
            for (Object element : elements) {
                if (element instanceof ResolvedArtifactSet) {
                    artifactSets.add((ResolvedArtifactSet) element);
                } else {
                    File file = (File) element;
                    artifactSets.add(new FileBackedArtifactSet(file));
                }
            }
            ParallelResolveArtifactSet.wrap(CompositeResolvedArtifactSet.of(artifactSets), buildOperationExecutor).visit(new ArtifactVisitorToResolvedFileVisitorAdapter(visitor));
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            // No dependencies
        }
    }

    // "partial" in the sense that some artifacts are only available as a File, and have no metadata
    private class PartialSelectedArtifactProvider implements ResolutionResultProvider<SelectedArtifactSet> {
        private final List<?> elements;

        public PartialSelectedArtifactProvider(List<?> elements) {
            this.elements = elements;
        }

        @Override
        public SelectedArtifactSet getTaskDependencyValue() {
            return getValue();
        }

        @Override
        public SelectedArtifactSet getValue() {
            return new PartialSelectedArtifactSet(elements, buildOperationExecutor);
        }
    }
}
