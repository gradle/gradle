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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ServiceScope(Scope.BuildTree.class)
public class ArtifactSetToFileCollectionFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final TaskDependencyFactory taskDependencyFactory;
    private final InternalProblems problemsService;

    public ArtifactSetToFileCollectionFactory(TaskDependencyFactory taskDependencyFactory, BuildOperationExecutor buildOperationExecutor, InternalProblems problemsService) {
        this.taskDependencyFactory = taskDependencyFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.problemsService = problemsService;
    }

    public ResolutionHost resolutionHost(String displayName) {
        return new NameBackedResolutionHost(problemsService, displayName);
    }

    /**
     * Presents the contents of the given artifacts as a partial {@link FileCollectionInternal} implementation.
     *
     * <p>This produces only a minimal implementation to use for artifact sets loaded from the configuration cache
     * Over time, this should be merged with the FileCollection implementation in DefaultConfiguration
     */
    public ResolutionBackedFileCollection asFileCollection(String displayName, boolean lenient, List<?> elements) {
        return new ResolutionBackedFileCollection(new PartialSelectedArtifactSet(elements, buildOperationExecutor), lenient, new NameBackedResolutionHost(problemsService, displayName), taskDependencyFactory);
    }

    public ResolvedArtifactSet asResolvedArtifactSet(Throwable failure) {
        return new BrokenResolvedArtifactSet(failure);
    }

    public ResolvedArtifactSet asResolvedArtifactSet(
        ComponentArtifactIdentifier id,
        AttributeContainer variantAttributes,
        ImmutableCapabilities capabilities,
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
    private static class NameBackedResolutionHost implements ResolutionHost, DisplayName {
        private final InternalProblems problemsService;
        private final String displayName;

        public NameBackedResolutionHost(InternalProblems problemsService, String displayName) {
            this.problemsService = problemsService;
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getCapitalizedDisplayName() {
            return StringUtils.capitalize(displayName);
        }

        @Override
        public DisplayName displayName() {
            return this;
        }

        @Override
        public InternalProblems getProblems() {
            return problemsService;
        }

        @Override
        public Optional<TypedResolveException> consolidateFailures(String resolutionType, Collection<Throwable> failures) {
            if (failures.isEmpty()) {
                return Optional.empty();
            } else {
                reportProblems(failures);
                return Optional.of(new TypedResolveException(resolutionType, displayName, failures));
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
}
