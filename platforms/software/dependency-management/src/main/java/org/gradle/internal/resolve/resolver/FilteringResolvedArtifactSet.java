/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.resolve.resolver;

import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.transform.TransformStepNode;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.function.Predicate;

/**
 * A {@link ResolvedArtifactSet} that applies a filter to the artifacts of a delegate {@link ResolvedArtifactSet}.
 * <p>
 * The filter is applied <strong>after</strong> build dependencies are calculated, meaning the filter is not
 * applied to build dependencies. This is because the filter may be a function of the resolved artifact files,
 * which are not known until after build dependencies are executed.
 */
class FilteringResolvedArtifactSet implements ResolvedArtifactSet {

    private final ResolvedArtifactSet artifacts;
    private final Predicate<ResolvableArtifact> filter;

    public FilteringResolvedArtifactSet(ResolvedArtifactSet artifacts, Predicate<ResolvableArtifact> filter) {
        this.artifacts = artifacts;
        this.filter = filter;
    }

    @Override
    public void visit(Visitor visitor) {
        artifacts.visit(new Visitor() {
            @Override
            public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
                return visitor.prepareForVisit(source);
            }

            @Override
            public void visitArtifacts(Artifacts artifacts) {
                visitor.visitArtifacts(new Artifacts() {
                    @Override
                    public void prepareForVisitingIfNotAlready() {
                        artifacts.prepareForVisitingIfNotAlready();
                    }

                    @Override
                    public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
                        artifacts.startFinalization(actions, requireFiles);
                    }

                    @Override
                    public void visit(ArtifactVisitor visitor) {
                        artifacts.visit(new ArtifactVisitor() {
                            @Override
                            public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
                                return visitor.prepareForVisit(source);
                            }

                            @Override
                            public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
                                if (filter.test(artifact)) {
                                    visitor.visitArtifact(variantName, variantAttributes, capabilities, artifact);
                                }
                            }

                            @Override
                            public boolean requireArtifactFiles() {
                                return visitor.requireArtifactFiles();
                            }

                            @Override
                            public void visitFailure(Throwable failure) {
                                visitor.visitFailure(failure);
                            }

                            @Override
                            public void endVisitCollection(FileCollectionInternal.Source source) {
                                visitor.endVisitCollection(source);
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public void visitTransformSources(TransformSourceVisitor visitor) {
        artifacts.visitTransformSources(new TransformSourceVisitor() {
            @Override
            public void visitArtifact(ResolvableArtifact artifact) {
                if (filter.test(artifact)) {
                    visitor.visitArtifact(artifact);
                }
            }

            @Override
            public void visitTransform(TransformStepNode source) {
                if (filter.test(source.getInputArtifact())) {
                    visitor.visitTransform(source);
                }
            }
        });
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        artifacts.visitExternalArtifacts(artifact -> {
            if (filter.test(artifact)) {
                visitor.execute(artifact);
            }
        });
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // Do not apply exclusions to build dependencies, in order to permit filters that are
        // a function of the artifact files.
        // This means that we might build some filtered artifacts, but we filter those later.
        artifacts.visitDependencies(context);
    }
}
