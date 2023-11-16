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
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType;
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity;
import org.gradle.operations.dependencies.variants.Capability;
import org.gradle.operations.dependencies.variants.ComponentIdentifier;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class TransformStepNode extends CreationOrderedNode implements SelfExecutingNode {

    protected final TransformStep transformStep;
    protected final ResolvableArtifact artifact;
    private final ComponentVariantIdentifier targetComponentVariant;
    private final AttributeContainer sourceAttributes;
    protected final TransformUpstreamDependencies upstreamDependencies;
    private final long transformStepNodeId;

    private PlannedTransformStepIdentity cachedIdentity;

    protected TransformStepNode(
        long transformStepNodeId,
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformStep transformStep,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies
    ) {
        this.targetComponentVariant = targetComponentVariant;
        this.sourceAttributes = sourceAttributes;
        this.transformStep = transformStep;
        this.artifact = artifact;
        this.upstreamDependencies = upstreamDependencies;
        this.transformStepNodeId = transformStepNodeId;
    }

    public long getTransformStepNodeId() {
        return transformStepNodeId;
    }

    public ComponentVariantIdentifier getTargetComponentVariant() {
        return targetComponentVariant;
    }

    public AttributeContainer getSourceAttributes() {
        return sourceAttributes;
    }

    public PlannedTransformStepIdentity getNodeIdentity() {
        if (cachedIdentity == null) {
            cachedIdentity = createIdentity();
        }
        return cachedIdentity;
    }

    private PlannedTransformStepIdentity createIdentity() {
        String consumerBuildPath = transformStep.getOwningProject().getBuildPath().toString();
        String consumerProjectPath = transformStep.getOwningProject().getProjectPath().toString();
        ComponentIdentifier componentId = ComponentToOperationConverter.convertComponentIdentifier(targetComponentVariant.getComponentId());
        Map<String, String> sourceAttributes = AttributesToMapConverter.convertToMap(this.sourceAttributes);
        Map<String, String> targetAttributes = AttributesToMapConverter.convertToMap(targetComponentVariant.getAttributes());
        List<Capability> capabilities = targetComponentVariant.getCapabilities().asSet().stream()
            .map(TransformStepNode::convertCapability)
            .collect(Collectors.toList());

        return new DefaultPlannedTransformStepIdentity(
            consumerBuildPath,
            consumerProjectPath,
            componentId,
            sourceAttributes,
            targetAttributes,
            capabilities,
            artifact.getArtifactName().getDisplayName(),
            upstreamDependencies.getConfigurationIdentity(),
            transformStepNodeId
        );
    }

    private static Capability convertCapability(org.gradle.api.capabilities.Capability capability) {
        return new Capability() {
            @Override
            public String getGroup() {
                return capability.getGroup();
            }

            @Override
            public String getName() {
                return capability.getName();
            }

            @Override
            public String getVersion() {
                return capability.getVersion();
            }

            @Override
            public String toString() {
                return getGroup() + ":" + getName() + (getVersion() == null ? "" : (":" + getVersion()));
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
        return transformStep.getOwningProject();
    }

    @Override
    public boolean isPublicNode() {
        return true;
    }

    @Override
    public String toString() {
        return transformStep.getDisplayName();
    }

    public TransformStep getTransformStep() {
        return transformStep;
    }

    public Try<TransformStepSubject> getTransformedSubject() {
        return getTransformedArtifacts().getValue();
    }

    @Override
    public void execute(NodeExecutionContext context) {
        getTransformedArtifacts().run(context);
    }

    public void executeIfNotAlready() {
        transformStep.isolateParametersIfNotAlready();
        upstreamDependencies.finalizeIfNotAlready();
        getTransformedArtifacts().finalizeIfNotAlready();
    }

    protected abstract CalculatedValueContainer<TransformStepSubject, ?> getTransformedArtifacts();

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

    public static class InitialTransformStepNode extends TransformStepNode {
        private final CalculatedValueContainer<TransformStepSubject, TransformInitialArtifact> result;

        public InitialTransformStepNode(
            long transformStepNodeId,
            ComponentVariantIdentifier targetComponentVariant,
            AttributeContainer sourceAttributes,
            TransformStep transformStep,
            ResolvableArtifact artifact,
            TransformUpstreamDependencies upstreamDependencies,
            BuildOperationExecutor buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStep, artifact, upstreamDependencies);
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformInitialArtifact(buildOperationExecutor));
        }

        @Override
        protected CalculatedValueContainer<TransformStepSubject, TransformInitialArtifact> getTransformedArtifacts() {
            return result;
        }

        protected class TransformInitialArtifact extends AbstractTransformArtifacts {

            public TransformInitialArtifact(BuildOperationExecutor buildOperationExecutor) {
                super(buildOperationExecutor);
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                super.visitDependencies(context);
                context.add(artifact);
            }

            @Override
            protected TransformStepBuildOperation createBuildOperation(NodeExecutionContext context) {
                return new TransformStepBuildOperation() {
                    @Override
                    protected TransformStepSubject transform() {
                        TransformStepSubject initialSubject;
                        try {
                            initialSubject = TransformStepSubject.initial(artifact);
                        } catch (ResolveException e) {
                            throw e;
                        } catch (RuntimeException e) {
                            throw new DefaultLenientConfiguration.ArtifactResolveException("artifacts", transformStep.getDisplayName(), Collections.singleton(e));
                        }

                        return transformStep
                            .createInvocation(initialSubject, upstreamDependencies, context)
                            .completeAndGet()
                            .get();
                    }

                    @Override
                    protected String describeSubject() {
                        return artifact.getId().getDisplayName();
                    }
                };
            }
        }
    }

    public static class ChainedTransformStepNode extends TransformStepNode {
        private final TransformStepNode previousTransformStepNode;
        private final CalculatedValueContainer<TransformStepSubject, TransformPreviousArtifacts> result;

        public ChainedTransformStepNode(
            long transformStepNodeId,
            ComponentVariantIdentifier targetComponentVariant,
            AttributeContainer sourceAttributes,
            TransformStep transformStep,
            TransformStepNode previousTransformStepNode,
            TransformUpstreamDependencies upstreamDependencies,
            BuildOperationExecutor buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStep, previousTransformStepNode.artifact, upstreamDependencies);
            this.previousTransformStepNode = previousTransformStepNode;
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformPreviousArtifacts(buildOperationExecutor));
        }

        public TransformStepNode getPreviousTransformStepNode() {
            return previousTransformStepNode;
        }

        @Override
        protected CalculatedValueContainer<TransformStepSubject, TransformPreviousArtifacts> getTransformedArtifacts() {
            return result;
        }

        @Override
        public void executeIfNotAlready() {
            // Only finalize the previous node when executing this node on demand
            previousTransformStepNode.executeIfNotAlready();
            super.executeIfNotAlready();
        }

        protected class TransformPreviousArtifacts extends AbstractTransformArtifacts {

            public TransformPreviousArtifacts(BuildOperationExecutor buildOperationExecutor) {
                super(buildOperationExecutor);
            }

            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                super.visitDependencies(context);
                context.add(new DefaultTransformNodeDependency(Collections.singletonList(previousTransformStepNode)));
            }

            @Override
            protected TransformStepBuildOperation createBuildOperation(NodeExecutionContext context) {
                return new TransformStepBuildOperation() {
                    @Override
                    protected TransformStepSubject transform() {
                        return previousTransformStepNode.getTransformedSubject()
                            .flatMap(transformedSubject -> transformStep
                                .createInvocation(transformedSubject, upstreamDependencies, context)
                                .completeAndGet())
                            .get();
                    }

                    @Override
                    protected String describeSubject() {
                        return previousTransformStepNode.getTransformedSubject()
                            .map(Describable::getDisplayName)
                            .getOrMapFailure(Throwable::getMessage);
                    }
                };
            }
        }
    }

    protected abstract class AbstractTransformArtifacts implements ValueCalculator<TransformStepSubject> {
        private final BuildOperationExecutor buildOperationExecutor;

        protected AbstractTransformArtifacts(BuildOperationExecutor buildOperationExecutor) {
            this.buildOperationExecutor = buildOperationExecutor;
        }

        @OverridingMethodsMustInvokeSuper
        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(transformStep);
            context.add(upstreamDependencies);
        }

        @Override
        public TransformStepSubject calculateValue(NodeExecutionContext context) {
            TransformStepBuildOperation buildOperation = createBuildOperation(context);
            ProjectInternal owningProject = transformStep.getOwningProject();
            return owningProject == null
                ? buildOperation.transform()
                : buildOperationExecutor.call(buildOperation);
        }

        protected abstract TransformStepBuildOperation createBuildOperation(NodeExecutionContext context);
    }

    protected abstract class TransformStepBuildOperation implements CallableBuildOperation<TransformStepSubject> {

        @UsedByScanPlugin("The string is used for filtering out artifact transform logs in Develocity")
        private static final String TRANSFORMING_PROGRESS_PREFIX = "Transforming ";

        @Override
        public final BuildOperationDescriptor.Builder description() {
            String transformStepName = transformStep.getDisplayName();
            String subjectName = describeSubject();
            String basicName = subjectName + " with " + transformStepName;
            return BuildOperationDescriptor.displayName("Transform " + basicName)
                .progressDisplayName(TRANSFORMING_PROGRESS_PREFIX + basicName)
                .metadata(BuildOperationCategory.TRANSFORM)
                .details(new ExecutePlannedTransformStepBuildOperationDetails(TransformStepNode.this, transformStepName, subjectName));
        }

        protected abstract String describeSubject();

        @Override
        public TransformStepSubject call(BuildOperationContext context) {
            context.setResult(RESULT);
            return transform();
        }

        protected abstract TransformStepSubject transform();
    }

    private static final ExecutePlannedTransformStepBuildOperationType.Result RESULT = new ExecutePlannedTransformStepBuildOperationType.Result() {
    };

}
