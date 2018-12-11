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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TransformationNode extends Node {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();

    private final int order = ORDER_COUNTER.incrementAndGet();
    protected final TransformationStep transformationStep;
    protected TransformationSubject transformedSubject;

    public static TransformationNode chained(TransformationStep current, TransformationNode previous, ExecutionGraphDependenciesResolver executionGraphDependenciesResolver) {
        return new ChainedTransformationNode(current, previous, executionGraphDependenciesResolver);
    }

    public static TransformationNode initial(TransformationStep initial, ResolvableArtifact artifact, ExecutionGraphDependenciesResolver executionGraphDependenciesResolver) {
        return new InitialTransformationNode(initial, artifact, executionGraphDependenciesResolver);
    }

    protected TransformationNode(TransformationStep transformationStep) {
        this.transformationStep = transformationStep;
    }

    public abstract void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener);

    @Override
    public String toString() {
        return transformationStep.getDisplayName();
    }

    private TransformationSubject getTransformedSubject() {
        if (transformedSubject == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
        return transformedSubject;
    }

    @Override
    public Set<Node> getFinalizers() {
        return Collections.emptySet();
    }


    @Override
    public void prepareForExecution() {
    }

    @Nullable
    @Override
    public Project getProject() {
        // Transforms do not require project state
        return null;
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
        TransformationNode otherTransformation = (TransformationNode) other;
        return order - otherTransformation.order;
    }

    protected void processDependencies(Action<Node> processHardSuccessor, Set<Node> dependencies) {
        for (Node dependency : dependencies) {
            addDependencySuccessor(dependency);
            processHardSuccessor.execute(dependency);
        }
    }

    private static class InitialTransformationNode extends TransformationNode {
        private final ResolvableArtifact artifact;
        private final ExecutionGraphDependenciesResolver dependenciesResolver;

        public InitialTransformationNode(TransformationStep transformationStep, ResolvableArtifact artifact, ExecutionGraphDependenciesResolver dependenciesResolver) {
            super(transformationStep);
            this.artifact = artifact;
            this.dependenciesResolver = dependenciesResolver;
        }

        @Override
        public void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            this.transformedSubject = buildOperationExecutor.call(new ArtifactTransformationStepBuildOperation() {
                @Override
                protected TransformationSubject transform() {
                    File file;
                    try {
                        file = artifact.getFile();
                    } catch (ResolveException e) {
                        return TransformationSubject.failure("artifact " + artifact.getId().getDisplayName(), e);
                    } catch (RuntimeException e) {
                        return TransformationSubject.failure("artifact " + artifact.getId().getDisplayName(),
                                new DefaultLenientConfiguration.ArtifactResolveException("artifacts", transformationStep.getDisplayName(), "artifact transform", Collections.singleton(e)));
                    }

                    TransformationSubject initialArtifactTransformationSubject = TransformationSubject.initial(artifact.getId(), file);
                    return transformationStep.transform(initialArtifactTransformationSubject, dependenciesResolver);
                }

                @Override
                protected String describeSubject() {
                    return "artifact " + artifact.getId().getDisplayName();
                }
            });
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
            processDependencies(processHardSuccessor, dependencyResolver.resolveDependenciesFor(null, artifact));
            processDependencies(processHardSuccessor, dependencyResolver.resolveDependenciesFor(null, dependenciesResolver.computeDependencyNodes(transformationStep)));
        }

    }

    private static class ChainedTransformationNode extends TransformationNode {
        private final TransformationNode previousTransformationNode;
        private final ExecutionGraphDependenciesResolver dependenciesResolver;

        public ChainedTransformationNode(TransformationStep transformationStep, TransformationNode previousTransformationNode, ExecutionGraphDependenciesResolver dependenciesResolver) {
            super(transformationStep);
            this.previousTransformationNode = previousTransformationNode;
            this.dependenciesResolver = dependenciesResolver;
        }

        @Override
        public void execute(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
            this.transformedSubject = buildOperationExecutor.call(new ArtifactTransformationStepBuildOperation() {
                @Override
                protected TransformationSubject transform() {
                    TransformationSubject transformedSubject = previousTransformationNode.getTransformedSubject();
                    if (transformedSubject.getFailure() != null) {
                        return transformedSubject;
                    }
                    return transformationStep.transform(transformedSubject, dependenciesResolver);
                }

                @Override
                protected String describeSubject() {
                    return previousTransformationNode.getTransformedSubject().getDisplayName();
                }
            });
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
            addDependencySuccessor(previousTransformationNode);
            processHardSuccessor.execute(previousTransformationNode);
            processDependencies(processHardSuccessor, dependencyResolver.resolveDependenciesFor(null, dependenciesResolver.computeDependencyNodes(transformationStep)));
        }

    }

    private abstract class ArtifactTransformationStepBuildOperation implements CallableBuildOperation<TransformationSubject> {

        @Override
        public final BuildOperationDescriptor.Builder description() {
            String transformerName = transformationStep.getDisplayName();
            String subjectName = describeSubject();
            String basicName = subjectName + " with " + transformerName;
            return BuildOperationDescriptor.displayName("Transform " + basicName)
                .progressDisplayName("Transforming " + basicName)
                .operationType(BuildOperationCategory.TRANSFORM)
                .details(new ExecuteScheduledTransformationStepBuildOperationDetails(TransformationNode.this, transformerName, subjectName));
        }

        protected abstract String describeSubject();

        @Override
        public TransformationSubject call(BuildOperationContext context) {
            TransformationSubject transformedSubject = transform();
            context.setResult(ExecuteScheduledTransformationStepBuildOperationType.RESULT);
            context.failed(transformedSubject.getFailure());
            return transformedSubject;
        }

        protected abstract TransformationSubject transform();
    }

}
