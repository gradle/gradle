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
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolveException;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TransformNode extends Node {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();

    private final int order = ORDER_COUNTER.incrementAndGet();
    protected final ArtifactTransformationStep artifactTransformer;
    protected TransformationSubject transformedSubject;

    public static TransformNode chained(ArtifactTransformationStep current, TransformNode previous) {
        return new ChainedTransformNode(current, previous);
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

    private TransformationSubject getTransformedSubject() {
        if (transformedSubject == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
        return transformedSubject;
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
            InitialArtifactTransformationStepOperation transformationStep = new InitialArtifactTransformationStepOperation(buildOperationExecutor);
            buildOperationExecutor.run(transformationStep);
            this.transformedSubject = transformationStep.getTransformedSubject();
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
            private TransformationSubject transformedSubject;

            private final BuildOperationExecutor buildOperationExecutor;
            public InitialArtifactTransformationStepOperation(BuildOperationExecutor buildOperationExecutor) {
                this.buildOperationExecutor = buildOperationExecutor;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Transform artifact " + artifactSet.getArtifactId().getDisplayName() + " with " + artifactTransformer.getDisplayName();
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
                    this.transformedSubject = new ResolveFailedTransformationSubject(artifactSet.getArtifactId(), failure);
                    return;
                }
                ResolvedArtifactResult artifact = Iterables.getOnlyElement(visitor.getArtifacts());
                InitialArtifactTransformationSubject initialArtifactTransformationSubject = new InitialArtifactTransformationSubject(artifact.getId(), artifact.getFile());

                this.transformedSubject = artifactTransformer.transform(initialArtifactTransformationSubject);
            }

            public TransformationSubject getTransformedSubject() {
                return transformedSubject;
            }
        }
    }

    private static class ChainedTransformNode extends TransformNode {
        private final TransformNode previousTransform;

        public ChainedTransformNode(ArtifactTransformationStep artifactTransformer, TransformNode previousTransform) {
            super(artifactTransformer);
            this.previousTransform = previousTransform;
        }

        @Override
        public void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            ChainedArtifactTransformStepOperation chainedArtifactTransformStep = new ChainedArtifactTransformStepOperation();
            buildOperationExecutor.run(chainedArtifactTransformStep);
            this.transformedSubject = chainedArtifactTransformStep.getTransformedSubject();
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
            addDependencySuccessor(previousTransform);
            processHardSuccessor.execute(previousTransform);
        }

        private class ChainedArtifactTransformStepOperation implements RunnableBuildOperation {

            private TransformationSubject transformedSubject;

            @Override
            public void run(BuildOperationContext context) {
                this.transformedSubject = artifactTransformer.transform(previousTransform.getTransformedSubject());
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                String displayName = "Transform " + previousTransform.getTransformedSubject().getDisplayName() + " with " + artifactTransformer.getDisplayName();
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName)
                    .operationType(BuildOperationCategory.TRANSFORM);
            }

            public TransformationSubject getTransformedSubject() {
                return transformedSubject;
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
