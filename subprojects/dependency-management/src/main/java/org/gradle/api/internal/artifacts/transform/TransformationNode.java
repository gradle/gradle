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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Describable;
import org.gradle.api.Named;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.component.ComponentIdentifier;
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
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TransformationNode extends CreationOrderedNode implements SelfExecutingNode {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final long transformationNodeId;
    private final AttributeContainer sourceAttributes;
    protected final TransformationStep transformationStep;
    protected final ResolvableArtifact artifact;
    protected final TransformUpstreamDependencies upstreamDependencies;

    private TransformationIdentity cachedIdentity;

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

    public TransformationIdentity getNodeIdentity() {
        if (cachedIdentity == null) {
            cachedIdentity = createIdentity();
        }
        return cachedIdentity;
    }

    private TransformationIdentity createIdentity() {
        String buildPath = transformationStep.getOwningProject().getBuildPath().toString();
        String projectPath = transformationStep.getOwningProject().getIdentityPath().toString();
        ComponentIdentifier componentId = getComponentIdentifier(artifact.getId().getComponentIdentifier());
        Map<String, String> sourceAttributes = convertToMap(this.sourceAttributes);
        Class<?> transformType = transformationStep.getTransformer().getImplementationClass();
        Map<String, String> fromAttributes = convertToMap(transformationStep.getFromAttributes());
        Map<String, String> toAttributes = convertToMap(transformationStep.getToAttributes());
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
            public ComponentIdentifier getComponentId() {
                return componentId;
            }

            @Override
            public Map<String, String> getSourceAttributes() {
                return sourceAttributes;
            }

            @Override
            public Class<?> getTransformType() {
                return transformType;
            }

            @Override
            public Map<String, String> getFromAttributes() {
                return fromAttributes;
            }

            @Override
            public Map<String, String> getToAttributes() {
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

    private static ComponentIdentifier getComponentIdentifier(org.gradle.api.artifacts.component.ComponentIdentifier componentId) {
        if (componentId instanceof ProjectComponentIdentifier) {
            return new DefaultProjectComponentIdentifier((ProjectComponentIdentifier) componentId);
        } else if (componentId instanceof ModuleComponentIdentifier) {
            return new DefaultModuleComponentIdentifier((ModuleComponentIdentifier) componentId);
        } else {
            return new DefaultUnknownComponentIdentifier(componentId);
        }
    }

    /**
     * Convert to a map preserving the order of the attributes.
     */
    private static Map<String, String> convertToMap(AttributeContainer attributes) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Attribute<?> attribute : attributes.keySet()) {
            String strValue = getAttributeValueAsString(attributes, attribute);
            builder.put(attribute.getName(), strValue);
        }
        return builder.build();
    }

    private static String getAttributeValueAsString(AttributeContainer attributeContainer, Attribute<?> attribute) {
        // We use the same algorithm that Gradle uses when desugaring these on the build op, so that we don't end up
        // with unexpectedly different values due to arrays or Named objects being converted to Strings differently.
        // See LazyDesugaringAttributeContainer in the gradle/gradle codebase.
        Object attributeValue = attributeContainer.getAttribute(attribute);
        if (attributeValue == null) {
            throw new IllegalStateException("No attribute value for " + attribute);
        }

        if (attributeValue instanceof Named) {
            return ((Named) attributeValue).getName();
        } else if (attributeValue instanceof Object[]) {
            // don't bother trying to handle primitive arrays specially
            return Arrays.toString((Object[]) attributeValue);
        } else {
            return attributeValue.toString();
        }
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

    private static class DefaultProjectComponentIdentifier implements org.gradle.api.internal.artifacts.component.ProjectComponentIdentifier, CustomOperationTraceSerialization {

        private final ProjectComponentIdentifier projectComponentIdentifier;

        public DefaultProjectComponentIdentifier(ProjectComponentIdentifier projectComponentIdentifier) {
            this.projectComponentIdentifier = projectComponentIdentifier;
        }

        @Override
        public String getBuildPath() {
            return projectComponentIdentifier.getBuild().getName();
        }

        @Override
        public String getProjectPath() {
            return projectComponentIdentifier.getProjectPath();
        }

        @Override
        public String toString() {
            return projectComponentIdentifier.getDisplayName();
        }

        @Override
        public Object getCustomOperationTraceSerializableModel() {
            ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
            builder.put("buildPath", getBuildPath());
            builder.put("projectPath", getProjectPath());
            return builder.build();
        }
    }

    private static class DefaultModuleComponentIdentifier implements org.gradle.api.internal.artifacts.component.ModuleComponentIdentifier, CustomOperationTraceSerialization {

        private final ModuleComponentIdentifier moduleComponentIdentifier;

        public DefaultModuleComponentIdentifier(ModuleComponentIdentifier moduleComponentIdentifier) {
            this.moduleComponentIdentifier = moduleComponentIdentifier;
        }

        @Override
        public String getGroup() {
            return moduleComponentIdentifier.getGroup();
        }

        @Override
        public String getModule() {
            return moduleComponentIdentifier.getModule();
        }

        @Override
        public String getVersion() {
            return moduleComponentIdentifier.getVersion();
        }

        @Override
        public String toString() {
            return moduleComponentIdentifier.getDisplayName();
        }

        @Override
        public Object getCustomOperationTraceSerializableModel() {
            ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
            builder.put("group", getGroup());
            builder.put("module", getModule());
            builder.put("version", getVersion());
            return builder.build();
        }
    }

    private static class DefaultUnknownComponentIdentifier implements org.gradle.api.internal.artifacts.component.UnknownComponentIdentifier, CustomOperationTraceSerialization {

        private final org.gradle.api.artifacts.component.ComponentIdentifier componentId;

        public DefaultUnknownComponentIdentifier(org.gradle.api.artifacts.component.ComponentIdentifier componentId) {
            this.componentId = componentId;
        }

        @Override
        public String getDisplayName() {
            return componentId.getDisplayName();
        }

        @Override
        public String getClassName() {
            return componentId.getClass().getName();
        }

        @Override
        public String toString() {
            return componentId.getDisplayName();
        }

        @Override
        public Object getCustomOperationTraceSerializableModel() {
            ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
            builder.put("displayName", getDisplayName());
            builder.put("className", getClassName());
            return builder.build();
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
