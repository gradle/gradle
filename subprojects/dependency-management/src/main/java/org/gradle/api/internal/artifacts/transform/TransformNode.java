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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildableSingleResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

public abstract class TransformNode extends Node {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();

    private final int order = ORDER_COUNTER.incrementAndGet();
    protected final ArtifactTransformationStep artifactTransformer;
    protected List<File> result;
    protected Throwable failure;

    public static TransformNode chained(ArtifactTransformationStep current, TransformNode previous, ComponentArtifactIdentifier artifactId) {
        return new ChainedTransformNode(current, previous, artifactId);
    }

    public static TransformNode initial(ArtifactTransformationStep initial, BuildableSingleResolvedArtifactSet artifact) {
        return new InitialTransformNode(initial, artifact);
    }

    protected TransformNode(ArtifactTransformationStep artifactTransformer) {
        this.artifactTransformer = artifactTransformer;
    }

    public abstract void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener);

    @Override
    public String toString() {
        return artifactTransformer.getDisplayName();
    }

    private List<File> getResult() {
        if (failure != null) {
            throw new IllegalStateException("Transformation has failed", failure);
        }
        if (result == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
        return result;
    }

    @Nullable
    private Throwable getFailure() {
        if (result == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
        return failure;
    }

    @Override
    public void prepareForExecution() {
    }

    @Override
    public void collectTaskInto(ImmutableCollection.Builder<Task> builder) {
    }

    @Override
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void rethrowNodeFailure() {
    }

    @Override
    public int compareTo(Node other) {
        if (getClass() != other.getClass()) {
            return getClass().getName().compareTo(other.getClass().getName());
        }
        TransformNode otherTransform = (TransformNode) other;
        return order - otherTransform.order;
    }

    private static class InitialTransformNode extends TransformNode {
        private final BuildableSingleResolvedArtifactSet artifactSet;

        public InitialTransformNode(
            ArtifactTransformationStep artifactTransformer,
            BuildableSingleResolvedArtifactSet artifactSet
        ) {
            super(artifactTransformer);
            this.artifactSet = artifactSet;
        }

        @Override
        public void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            InitialArtifactTransformationStepOperation transformationStep = new InitialArtifactTransformationStepOperation(buildOperationExecutor, transformListener);
            buildOperationExecutor.run(transformationStep);
            this.result = transformationStep.getResult();
            this.failure = transformationStep.getFailure();
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
            Set<Node> dependencies = getDependencies(dependencyResolver);
            for (Node dependency : dependencies) {
                addDependencySuccessor(dependency);
                processHardSuccessor.execute(dependency);
            }
        }

        private Set<Node> getDependencies(TaskDependencyResolver dependencyResolver) {
            return dependencyResolver.resolveDependenciesFor(null, artifactSet.getBuildDependencies());
        }

        private class InitialArtifactTransformationStepOperation implements RunnableBuildOperation {
            private List<File> result;
            private Throwable failure;
            private final BuildOperationExecutor buildOperationExecutor;
            private final ArtifactTransformListener transformListener;

            public InitialArtifactTransformationStepOperation(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
                this.buildOperationExecutor = buildOperationExecutor;
                this.transformListener = transformListener;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Transform " + artifactSet.getArtifactId().getDisplayName() + " with " + artifactTransformer.getDisplayName();
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName)
                    .operationType(BuildOperationCategory.TRANSFORM);
            }

            @Override
            public void run(BuildOperationContext context) {
                ResolveArtifacts resolveArtifacts = new ResolveArtifacts(artifactSet);
                buildOperationExecutor.runAll(resolveArtifacts);
                ResolvedArtifactCollectingVisitor visitor = new ResolvedArtifactCollectingVisitor();
                resolveArtifacts.getResult().visit(visitor);
                Set<Throwable> failures = visitor.getFailures();
                if (!failures.isEmpty()) {
                    Throwable failure;
                    if (failures.size() == 1 && Iterables.getOnlyElement(failures) instanceof ResolveException) {
                        failure = Iterables.getOnlyElement(failures);
                    } else {
                        failure = new DefaultLenientConfiguration.ArtifactResolveException("artifacts", artifactTransformer.getDisplayName(), "artifact transform", failures);
                    }
                    this.failure = failure;
                    this.result = Collections.emptyList();
                    return;
                }
                ResolvedArtifactResult artifact = Iterables.getOnlyElement(visitor.getArtifacts());

                TransformArtifactOperation operation = new TransformArtifactOperation(artifact.getId(), artifact.getFile(), artifactTransformer, transformListener);
                operation.run(context);
                this.failure = operation.getFailure();
                this.result = operation.getResult();
            }

            public List<File> getResult() {
                return result;
            }

            public Throwable getFailure() {
                return failure;
            }
        }
    }

    private static class ChainedTransformNode extends TransformNode {
        private final TransformNode previousTransform;
        private final ComponentArtifactIdentifier artifactId;

        public ChainedTransformNode(ArtifactTransformationStep artifactTransformer, TransformNode previousTransform, ComponentArtifactIdentifier artifactId) {
            super(artifactTransformer);
            this.previousTransform = previousTransform;
            this.artifactId = artifactId;
        }

        @Override
        public void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            ChainedArtifactTransformStepOperation chainedArtifactTransformStep = new ChainedArtifactTransformStepOperation(transformListener);
            buildOperationExecutor.run(chainedArtifactTransformStep);
            this.result = chainedArtifactTransformStep.getResult();
            this.failure = chainedArtifactTransformStep.getFailure();
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
            addDependencySuccessor(previousTransform);
            processHardSuccessor.execute(previousTransform);
        }

        private class ChainedArtifactTransformStepOperation implements RunnableBuildOperation {

            private final ArtifactTransformListener transformListener;
            private List<File> result;
            private Throwable failure;

            public ChainedArtifactTransformStepOperation(ArtifactTransformListener transformListener) {
                this.transformListener = transformListener;
            }

            @Override
            public void run(BuildOperationContext context) {
                Throwable previousFailure = previousTransform.getFailure();
                if (previousFailure != null) {
                    this.failure = previousFailure;
                    this.result = Collections.emptyList();
                    return;
                }
                ImmutableList.Builder<File> builder = ImmutableList.builder();
                for (File inputFile : previousTransform.getResult()) {
                    TransformFileOperation operation = new TransformFileOperation(inputFile, artifactTransformer, transformListener);
                    operation.run(context);
                    if (operation.getFailure() != null) {
                        this.failure = operation.getFailure();
                        this.result = Collections.emptyList();
                        return;
                    }
                    List<File> result = operation.getResult();
                    if (result != null) {
                        builder.addAll(result);
                    }
                }
                this.result = builder.build();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Transform " + artifactId.getDisplayName() + " with " + artifactTransformer.getDisplayName();
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName)
                    .operationType(BuildOperationCategory.TRANSFORM);
            }

            public List<File> getResult() {
                return result;
            }

            public Throwable getFailure() {
                return failure;
            }
        }
    }

    private static class ResolveArtifacts implements Action<BuildOperationQueue<RunnableBuildOperation>> {
        private final ResolvedArtifactSet artifactSet;
        private ResolvedArtifactSet.Completion result;

        public ResolveArtifacts(ResolvedArtifactSet artifactSet) {
            this.artifactSet = artifactSet;
        }

        @Override
        public void execute(BuildOperationQueue<RunnableBuildOperation> actions) {
            result = artifactSet.startVisit(actions, new ResolveOnlyAsyncArtifactListener());
        }

        public ResolvedArtifactSet.Completion getResult() {
            return result;
        }
    }

    private static class ResolveOnlyAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
        @Override
        public void artifactAvailable(ResolvableArtifact artifact) {
        }

        @Override
        public boolean requireArtifactFiles() {
            return true;
        }

        @Override
        public boolean includeFileDependencies() {
            return false;
        }

        @Override
        public void fileAvailable(File file) {
        }
    }
}
