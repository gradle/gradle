/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Buildable;
import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public abstract class ArtifactTransformTask extends DefaultTask implements ArtifactTransformResult {

    private final ArtifactTransformer transform;
    protected ConcurrentHashMap<ComponentArtifactIdentifier, TransformationResult> artifactResults;
    private final WorkerLeaseService workerLeaseService;

    @Inject
    public ArtifactTransformTask(UserCodeBackedTransformer transform, WorkerLeaseService workerLeaseService) {
        this.transform = transform;
        this.workerLeaseService = workerLeaseService;
    }

    @Internal
    @Override
    public ResolvedArtifactSet.Completion getResult(AttributeContainerInternal attributes) {
        return new TransformingResult(resolveArtifacts(), artifactResults, attributes);
    }

    public abstract ResolvedArtifactSet.Completion resolveArtifacts();

    public abstract TransformationResult incomingTransformationResult(ResolvableArtifact artifact);

    @TaskAction
    public void transformArtifacts() {
        workerLeaseService.withoutProjectLock(new Runnable() {
            @Override
            public void run() {
                artifactResults = new ConcurrentHashMap<ComponentArtifactIdentifier, TransformationResult>();
                ResolvedArtifactSet.Completion resolvedArtifacts = resolveArtifacts();
                resolvedArtifacts.visit(new ArtifactVisitor() {
                    @Override
                    public void visitArtifact(String variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
                        TransformationResult incoming = incomingTransformationResult(artifact);
                        artifactResults.put(artifact.getId(), transform(incoming));
                    }

                    @Override
                    public void visitFile(ComponentArtifactIdentifier artifactIdentifier, String variantName, AttributeContainer variantAttributes, File file) {
                        throw new UnsupportedOperationException("This artifact transform task does not support transforming files - File: " + file);
                    }

                    private TransformationResult transform(File file) {
                        try {
                            List<File> result = transform.transform(file);
                            return new TransformationResult(result);
                        } catch (Throwable e) {
                            return new TransformationResult(e);
                        }
                    }

                    private TransformationResult transform(TransformationResult incoming) {
                        if (incoming.isFailed()) {
                            return incoming;
                        }
                        ImmutableList.Builder<File> builder = ImmutableList.builder();
                        for (File file : incoming.getTransformedFiles()) {
                            TransformationResult transformationResult = transform(file);
                            if (transformationResult.isFailed()) {
                                return transformationResult;
                            }
                            builder.addAll(transformationResult.getTransformedFiles());
                        }
                        return new TransformationResult(builder.build());
                    }

                    @Override
                    public boolean requireArtifactFiles() {
                        return true;
                    }

                    @Override
                    public boolean includeFiles() {
                        return true;
                    }

                    @Override
                    public void visitFailure(Throwable failure) {

                    }
                });
            }
        });
    }

    @Inject
    public BuildOperationExecutor getBuildOperationExecuter() {
        throw new UnsupportedOperationException();
    }

    public static class TransformingResult implements ResolvedArtifactSet.Completion {
        private final ResolvedArtifactSet.Completion result;
        private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;
        private final AttributeContainerInternal attributes;

        public TransformingResult(ResolvedArtifactSet.Completion result, Map<ComponentArtifactIdentifier, TransformationResult> artifactResults, AttributeContainerInternal attributes) {
            this.result = result;
            this.artifactResults = artifactResults;
            this.attributes = attributes;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            result.visit(new ArtifactTransformingVisitor(visitor, attributes, artifactResults));
        }
    }

    public static class TransformationResult {
        private final List<File> transformedFiles;
        private Throwable failure;

        public TransformationResult(List<File> transformedFiles) {
            this.transformedFiles = transformedFiles;
            this.failure = null;
        }

        public TransformationResult(Throwable failure) {
            this.transformedFiles = null;
            this.failure = failure;
        }

        public boolean isFailed() {
            return failure != null;
        }

        public List<File> getTransformedFiles() {
            return Preconditions.checkNotNull(transformedFiles);
        }

        public Throwable getFailure() {
            return Preconditions.checkNotNull(failure);
        }
    }


    private static class ArtifactTransformingVisitor implements ArtifactVisitor {
        private final ArtifactVisitor visitor;
        private final AttributeContainerInternal target;
        private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;

        ArtifactTransformingVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Map<ComponentArtifactIdentifier, TransformationResult> artifactResults) {
            this.visitor = visitor;
            this.target = target;
            this.artifactResults = artifactResults;
        }

        @Override
        public void visitArtifact(String variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
            TransformationResult result = artifactResults.get(artifact.getId());
            if (result.isFailed()) {
                visitor.visitFailure(result.getFailure());
                return;
            }

            ResolvedArtifact sourceArtifact = artifact.toPublicView();
            List<File> transformedFiles = result.getTransformedFiles();
            TaskDependency buildDependencies = ((Buildable) artifact).getBuildDependencies();

            for (File output : transformedFiles) {
                IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(output, sourceArtifact.getClassifier());
                ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(sourceArtifact.getId().getComponentIdentifier(), artifactName);
                DefaultResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(sourceArtifact.getModuleVersion().getId(), artifactName, newId, buildDependencies, output);
                visitor.visitArtifact(variantName, target, resolvedArtifact);
            }
        }

        @Override
        public void visitFailure(Throwable failure) {
            visitor.visitFailure(failure);
        }

        @Override
        public boolean includeFiles() {
            return visitor.includeFiles();
        }

        @Override
        public boolean requireArtifactFiles() {
            return visitor.requireArtifactFiles();
        }

        @Override
        public void visitFile(ComponentArtifactIdentifier artifactIdentifier, String variantName, AttributeContainer variantAttributes, File file) {
            throw new UnsupportedOperationException("This artifact transform task does not support transforming files - File: " + file);
        }
    }
}
