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
import org.gradle.api.Describable;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.SelfExecutingNode;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.internal.Describables;
import org.gradle.internal.Try;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TransformationNode extends Node implements SelfExecutingNode {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();

    private final int order = ORDER_COUNTER.incrementAndGet();
    protected final TransformationStep transformationStep;
    protected final ResolvableArtifact artifact;
    protected final TransformUpstreamDependencies upstreamDependencies;

    public static ChainedTransformationNode chained(TransformationStep current, TransformationNode previous, TransformUpstreamDependencies upstreamDependencies, BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        return new ChainedTransformationNode(current, previous, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    public static InitialTransformationNode initial(TransformationStep initial, ResolvableArtifact artifact, TransformUpstreamDependencies upstreamDependencies, BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        return new InitialTransformationNode(initial, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    protected TransformationNode(TransformationStep transformationStep, ResolvableArtifact artifact, TransformUpstreamDependencies upstreamDependencies) {
        this.transformationStep = transformationStep;
        this.artifact = artifact;
        this.upstreamDependencies = upstreamDependencies;
    }

    public ResolvableArtifact getInputArtifact() {
        return artifact;
    }

    public TransformUpstreamDependencies getUpstreamDependencies() {
        return upstreamDependencies;
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        return transformationStep.getOwningProject();
    }

    @Override
    public boolean isPublicNode() {
        return true;
    }

    @Override
    public boolean requiresMonitoring() {
        return false;
    }

    @Override
    public void resolveMutations() {
        // Assume for now that no other node is going to destroy the transform outputs, or overlap with them
    }

    @Override
    public String toString() {
        return transformationStep.getDisplayName();
    }

    public TransformationStep getTransformationStep() {
        return transformationStep;
    }

    public Try<TransformationSubject> getTransformedSubject() {
        return getTransformedArtifacts().getValue();
    }

    @Override
    public void execute(NodeExecutionContext context) {
        getTransformedArtifacts().run(context);
    }

    public void executeIfNotAlready() {
        transformationStep.isolateParametersIfNotAlready();
        upstreamDependencies.finalizeIfNotAlready();
        getTransformedArtifacts().finalizeIfNotAlready();
    }

    protected abstract CalculatedValueContainer<TransformationSubject, ?> getTransformedArtifacts();

    @Override
    public Set<Node> getFinalizers() {
        return Collections.emptySet();
    }

    @Override
    public void prepareForExecution() {
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        // Transforms do not require project state
        return null;
    }

    @Override
    public List<ResourceLock> getResourcesToLock() {
        return Collections.emptyList();
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

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
        processDependencies(processHardSuccessor, dependencyResolver.resolveDependenciesFor(null, (TaskDependencyContainer) context -> getTransformedArtifacts().visitDependencies(context)));
    }

    protected void processDependencies(Action<Node> processHardSuccessor, Set<Node> dependencies) {
        for (Node dependency : dependencies) {
            addDependencySuccessor(dependency);
            processHardSuccessor.execute(dependency);
        }
    }

    public static class InitialTransformationNode extends TransformationNode {
        private final CalculatedValueContainer<TransformationSubject, TransformInitialArtifact> result;

        public InitialTransformationNode(TransformationStep transformationStep, ResolvableArtifact artifact, TransformUpstreamDependencies upstreamDependencies, BuildOperationExecutor buildOperationExecutor, CalculatedValueContainerFactory calculatedValueContainerFactory) {
            super(transformationStep, artifact, upstreamDependencies);
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformInitialArtifact(buildOperationExecutor));
        }

        @Override
        protected CalculatedValueContainer<TransformationSubject, TransformInitialArtifact> getTransformedArtifacts() {
            return result;
        }

        private class TransformInitialArtifact implements ValueCalculator<TransformationSubject> {
            private final BuildOperationExecutor buildOperationExecutor;

            public TransformInitialArtifact(BuildOperationExecutor buildOperationExecutor) {
                this.buildOperationExecutor = buildOperationExecutor;
            }

            @Override
            public boolean usesMutableProjectState() {
                // Transforms do not require access to any mutable model state
                return false;
            }

            @Nullable
            @Override
            public ProjectInternal getOwningProject() {
                return null;
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(transformationStep);
                context.add(upstreamDependencies);
                context.add(artifact);
            }

            @Override
            public TransformationSubject calculateValue(NodeExecutionContext context) {
                return buildOperationExecutor.call(new ArtifactTransformationStepBuildOperation() {
                    @Override
                    protected TransformationSubject transform() {
                        TransformationSubject initialArtifactTransformationSubject;
                        try {
                            initialArtifactTransformationSubject = TransformationSubject.initial(artifact);
                        } catch (ResolveException e) {
                            throw e;
                        } catch (RuntimeException e) {
                            throw new DefaultLenientConfiguration.ArtifactResolveException("artifacts", transformationStep.getDisplayName(), "artifact transform", Collections.singleton(e));
                        }

                        return transformationStep.createInvocation(initialArtifactTransformationSubject, upstreamDependencies, context).invoke().get();
                    }

                    @Override
                    protected String describeSubject() {
                        return artifact.getId().getDisplayName();
                    }
                });
            }
        }
    }

    public static class ChainedTransformationNode extends TransformationNode {
        private final TransformationNode previousTransformationNode;
        private final CalculatedValueContainer<TransformationSubject, TransformPreviousArtifacts> result;

        public ChainedTransformationNode(TransformationStep transformationStep,
                                         TransformationNode previousTransformationNode,
                                         TransformUpstreamDependencies upstreamDependencies,
                                         BuildOperationExecutor buildOperationExecutor,
                                         CalculatedValueContainerFactory calculatedValueContainerFactory) {
            super(transformationStep, previousTransformationNode.artifact, upstreamDependencies);
            this.previousTransformationNode = previousTransformationNode;
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformPreviousArtifacts(buildOperationExecutor));
        }

        public TransformationNode getPreviousTransformationNode() {
            return previousTransformationNode;
        }

        @Override
        protected CalculatedValueContainer<TransformationSubject, TransformPreviousArtifacts> getTransformedArtifacts() {
            return result;
        }

        @Override
        public void executeIfNotAlready() {
            // Only finalize the previous node when executing this node on demand
            previousTransformationNode.executeIfNotAlready();
            super.executeIfNotAlready();
        }

        private class TransformPreviousArtifacts implements ValueCalculator<TransformationSubject> {
            private final BuildOperationExecutor buildOperationExecutor;

            public TransformPreviousArtifacts(BuildOperationExecutor buildOperationExecutor) {
                this.buildOperationExecutor = buildOperationExecutor;
            }

            @Override
            public boolean usesMutableProjectState() {
                // Transforms do not require access to any mutable model state
                return false;
            }

            @Nullable
            @Override
            public ProjectInternal getOwningProject() {
                return null;
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(transformationStep);
                context.add(upstreamDependencies);
                context.add(new DefaultTransformationDependency(Collections.singletonList(previousTransformationNode)));
            }

            @Override
            public TransformationSubject calculateValue(NodeExecutionContext context) {
                return buildOperationExecutor.call(new ArtifactTransformationStepBuildOperation() {
                    @Override
                    protected TransformationSubject transform() {
                        return previousTransformationNode.getTransformedSubject().flatMap(transformedSubject ->
                            transformationStep.createInvocation(transformedSubject, upstreamDependencies, context).invoke()).get();
                    }

                    @Override
                    protected String describeSubject() {
                        return previousTransformationNode.getTransformedSubject()
                            .map(Describable::getDisplayName)
                            .getOrMapFailure(Throwable::getMessage);
                    }
                });
            }
        }
    }

    private abstract class ArtifactTransformationStepBuildOperation implements CallableBuildOperation<TransformationSubject> {

        @UsedByScanPlugin("The string is used for filtering out artifact transform logs in Gradle Enterprise")
        private static final String TRANSFORMING_PROGRESS_PREFIX = "Transforming ";

        @Override
        public final BuildOperationDescriptor.Builder description() {
            String transformerName = transformationStep.getDisplayName();
            String subjectName = describeSubject();
            String basicName = subjectName + " with " + transformerName;
            return BuildOperationDescriptor.displayName("Transform " + basicName)
                .progressDisplayName(TRANSFORMING_PROGRESS_PREFIX + basicName)
                .metadata(BuildOperationCategory.TRANSFORM)
                .details(new ExecuteScheduledTransformationStepBuildOperationDetails(TransformationNode.this, transformerName, subjectName));
        }

        protected abstract String describeSubject();

        @Override
        public TransformationSubject call(BuildOperationContext context) {
            context.setResult(ExecuteScheduledTransformationStepBuildOperationType.RESULT);
            return transform();
        }

        protected abstract TransformationSubject transform();
    }

}
