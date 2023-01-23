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

import org.gradle.api.Describable;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.execution.plan.CreationOrderedNode;
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
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.TransformationIdentity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TransformationNode extends CreationOrderedNode implements SelfExecutingNode {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final long transformationNodeId;
    private final AttributeContainer sourceAttributes;
    protected final TransformationStep transformationStep;
    protected final ResolvableArtifact artifact;
    protected final TransformUpstreamDependencies upstreamDependencies;

    public static ChainedTransformationNode chained(
        AttributeContainer sourceAttributes,
        TransformationStep current,
        TransformationNode previous,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return new ChainedTransformationNode(sourceAttributes, current, previous, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    public static InitialTransformationNode initial(
        AttributeContainer sourceAttributes,
        TransformationStep initial,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return new InitialTransformationNode(sourceAttributes, initial, artifact, upstreamDependencies, buildOperationExecutor, calculatedValueContainerFactory);
    }

    private static long createId() {
        return SEQUENCE.incrementAndGet();
    }

    protected TransformationNode(
        AttributeContainer sourceAttributes,
        TransformationStep transformationStep,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies
    ) {
        this.transformationNodeId = createId();
        this.sourceAttributes = sourceAttributes;
        this.transformationStep = transformationStep;
        this.artifact = artifact;
        this.upstreamDependencies = upstreamDependencies;
    }

    public long getTransformationNodeId() {
        return transformationNodeId;
    }

    public AttributeContainer getSourceAttributes() {
        return sourceAttributes;
    }

    @Nonnull
    @Override
    public TransformationIdentity getNodeIdentity() {
        String buildPath = transformationStep.getOwningProject().getBuildPath().toString();
        String projectPath = transformationStep.getOwningProject().getIdentityPath().toString();
        String componentId = artifact.getId().getComponentIdentifier().getDisplayName();
        String sourceAttributes = this.sourceAttributes.toString();
        Class<?> transformType = transformationStep.getTransformer().getImplementationClass();
        String fromAttributes = transformationStep.getFromAttributes().toString();
        String toAttributes = transformationStep.getToAttributes().toString();
        long transformationNodeId = getTransformationNodeId();

        return new TransformationIdentity() {

            @Override
            public String getBuildPath() {
                return buildPath;
            }

            @Override
            public String getProjectPath() {
                return projectPath;
            }

            @Override
            public String getComponentId() {
                return componentId;
            }

            @Override
            public String getSourceAttributes() {
                return sourceAttributes;
            }

            @Override
            public Class<?> getTransformType() {
                return transformType;
            }

            @Override
            public String getFromAttributes() {
                return fromAttributes;
            }

            @Override
            public String getToAttributes() {
                return toAttributes;
            }

            @Override
            public long getTransformationNodeId() {
                return transformationNodeId;
            }

            @Override
            public String toString() {
                return "Transform '" + componentId + "' with " + transformType.getName();
            }
        };
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
    public Throwable getNodeFailure() {
        return null;
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        processDependencies(dependencyResolver.resolveDependenciesFor(null, (TaskDependencyContainer) context -> getTransformedArtifacts().visitDependencies(context)));
    }

    protected void processDependencies(Set<Node> dependencies) {
        for (Node dependency : dependencies) {
            addDependencySuccessor(dependency);
        }
    }

    public static class InitialTransformationNode extends TransformationNode {
        private final CalculatedValueContainer<TransformationSubject, TransformInitialArtifact> result;

        public InitialTransformationNode(
            AttributeContainer sourceAttributes,
            TransformationStep transformationStep,
            ResolvableArtifact artifact,
            TransformUpstreamDependencies upstreamDependencies,
            BuildOperationExecutor buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(sourceAttributes, transformationStep, artifact, upstreamDependencies);
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

                        return transformationStep
                            .createInvocation(initialArtifactTransformationSubject, upstreamDependencies, context)
                            .completeAndGet()
                            .get();
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

        public ChainedTransformationNode(
            AttributeContainer sourceAttributes,
            TransformationStep transformationStep,
            TransformationNode previousTransformationNode,
            TransformUpstreamDependencies upstreamDependencies,
            BuildOperationExecutor buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(sourceAttributes, transformationStep, previousTransformationNode.artifact, upstreamDependencies);
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
                        return previousTransformationNode.getTransformedSubject()
                            .flatMap(transformedSubject -> transformationStep
                                .createInvocation(transformedSubject, upstreamDependencies, context)
                                .completeAndGet())
                            .get();
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
