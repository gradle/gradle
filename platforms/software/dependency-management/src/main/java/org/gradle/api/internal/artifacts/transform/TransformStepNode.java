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
import org.gradle.api.Task;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.execution.plan.CreationOrderedNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.SelfExecutingNode;
import org.gradle.execution.plan.TaskDeclarationAware;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.internal.Describables;
import org.gradle.internal.Try;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.execution.SafeInternalArtifactTransformAccess;
import org.gradle.internal.execution.WorkExecutionTracker;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType;
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity;
import org.gradle.operations.dependencies.variants.Capability;
import org.gradle.operations.dependencies.variants.ComponentIdentifier;
import org.jspecify.annotations.Nullable;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("this-escape")
public abstract class TransformStepNode extends CreationOrderedNode implements SelfExecutingNode, TaskDeclarationAware {

    protected final TransformStep transformStep;
    protected final ResolvableArtifact artifact;
    private final ComponentVariantIdentifier targetComponentVariant;
    private final AttributeContainer sourceAttributes;
    protected final TransformUpstreamDependencies upstreamDependencies;
    private final WorkExecutionTracker workExecutionTracker;
    private final long transformStepNodeId;
    @Nullable
    private PlannedTransformStepIdentity cachedIdentity;
    private final Set<String> declaringTaskPaths = ConcurrentHashMap.newKeySet();

    /**
     * Per-build set of task paths for which the undeclared-resolution deprecation has already been emitted
     * by {@link #nagAboutUndeclaredResolution()}.
     * <p>
     * Used purely to dedupe warnings: a single task action that queries this node multiple times
     * (e.g. several {@code view.files} accesses in one {@code doLast}) should only see one warning,
     * not one per call site.
     * <p>
     * This set is intentionally NOT serialized across configuration cache reloads: it is UX-only state,
     * not correctness state. On CC reload it starts empty and undeclared tasks will warn again on first
     * query, which is the desired behavior since the user has not yet fixed their build.
     */
    private final Set<String> naggedTaskPaths = ConcurrentHashMap.newKeySet();

    protected TransformStepNode(
        long transformStepNodeId,
        ComponentVariantIdentifier targetComponentVariant,
        AttributeContainer sourceAttributes,
        TransformStep transformStep,
        ResolvableArtifact artifact,
        TransformUpstreamDependencies upstreamDependencies,
        WorkExecutionTracker workExecutionTracker
    ) {
        this.targetComponentVariant = targetComponentVariant;
        this.sourceAttributes = sourceAttributes;
        this.transformStep = transformStep;
        this.artifact = artifact;
        this.upstreamDependencies = upstreamDependencies;
        this.workExecutionTracker = workExecutionTracker;
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
        ProjectIdentity projectId = transformStep.getOwningProject().getProjectIdentity();
        String consumerBuildPath = projectId.getBuildPath().asString();
        String consumerProjectPath = projectId.getProjectPath().asString();
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

    /**
     * Records that the given task properly declared this transform step node as one of its inputs.
     * Called by {@link TransformStepNodeDependencyResolver} as part of task dependency resolution.
     * Used by {@link #executeIfNotAlready()} to identify undeclared queries on a per-task basis.
     */
    @Override
    public void markDeclaredBy(Task task) {
        declaringTaskPaths.add(task.getPath());
    }

    /**
     * Returns the set of task paths that declared this node as an input. Used by the configuration
     * cache codec to serialize per-node declarer state so that it survives CC reloads without
     * requiring the dependency resolver to re-run.
     */
    public Set<String> getDeclaringTaskPaths() {
        return declaringTaskPaths;
    }

    /**
     * Restores the declaring task paths after configuration cache deserialization. The dependency
     * resolver does not re-run on CC reload, so without restoration the set would be empty and
     * every properly-declared task would falsely trip the undeclared-input nag.
     */
    public void setDeclaringTaskPaths(Set<String> taskPaths) {
        declaringTaskPaths.addAll(taskPaths);
    }

    @Override
    public void execute(NodeExecutionContext context) {
        getTransformedArtifacts().run(context);
    }

    public void executeIfNotAlready() {
        // The check is gated on whether the currently-executing task declared this node as an
        // input, not on whether the node has been executed: a properly-declared sibling task that
        // pre-runs the transform must not silence an undeclared querier later in the build.
        // naggedTaskPaths.add(...) dedupes multiple query sites within the same task action so
        // the user sees one warning per (undeclared task, node) per build, not one per call site.
        if (!SafeInternalArtifactTransformAccess.isActive()) {
            Optional<TaskInternal> currentTask = workExecutionTracker.getCurrentTask();
            if (currentTask.isPresent()) {
                String taskPath = currentTask.get().getPath();
                if (!declaringTaskPaths.contains(taskPath) && naggedTaskPaths.add(taskPath)) {
                    nagAboutUndeclaredResolution();
                }
            }
        }
        transformStep.isolateParametersIfNotAlready();
        upstreamDependencies.finalizeIfNotAlready();
        getTransformedArtifacts().finalizeIfNotAlready();
    }

    private void nagAboutUndeclaredResolution() {
        String taskPath = workExecutionTracker.getCurrentTask().map(Task::getPath).orElse(null);
        String configName = configurationNameOf();
        DeprecationMessageBuilder<?> deprecation = DeprecationLogger.deprecate(
            "Querying the output of an artifact transform from a task action without declaring it as a task input"
        );
        // Only add the contextual line when both task and configuration are known — otherwise the
        // string would render misleading filler like "Task 'null' queried ...".
        if (taskPath != null && configName != null) {
            deprecation = deprecation.withContext(String.format(
                "Task '%s' queried artifact transform output of configuration '%s' without declaring it as an input.",
                taskPath, configName
            ));
        }

        deprecation.withAdvice("Declare the files or artifacts produced by the configuration using the transform as a task input to properly wire it into the execution plan.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "undeclared_artifact_transform_input")
            .nagUser();
    }

    @Nullable
    private String configurationNameOf() {
        ConfigurationIdentity id = getUpstreamDependencies().getConfigurationIdentity();
        return id == null ? null : id.getName();
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
            WorkExecutionTracker workExecutionTracker,
            BuildOperationRunner buildOperationRunner,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStep, artifact, upstreamDependencies, workExecutionTracker);
            result = calculatedValueContainerFactory.create(Describables.of(this), new TransformInitialArtifact(buildOperationRunner));
        }

        @Override
        protected CalculatedValueContainer<TransformStepSubject, TransformInitialArtifact> getTransformedArtifacts() {
            return result;
        }

        protected class TransformInitialArtifact extends AbstractTransformArtifacts {

            public TransformInitialArtifact(BuildOperationRunner buildOperationRunner) {
                super(buildOperationRunner);
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
                        return transformStep
                            .createInvocation(TransformStepSubject.initial(artifact), upstreamDependencies, context)
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
            WorkExecutionTracker workExecutionTracker,
            BuildOperationRunner buildOperationExecutor,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(transformStepNodeId, targetComponentVariant, sourceAttributes, transformStep, previousTransformStepNode.artifact, upstreamDependencies, workExecutionTracker);
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
        public void markDeclaredBy(Task task) {
            // Propagate the declaration through the chain: declaring an artifact view as a task
            // input implicitly declares every upstream transform step that produces files in
            // that view. Without this, only the final node would be marked and intermediate
            // nodes would falsely trip the undeclared-input nag when the task action queries
            // the view and walks the whole chain.
            super.markDeclaredBy(task);
            previousTransformStepNode.markDeclaredBy(task);
        }

        @Override
        public void executeIfNotAlready() {
            // Only finalize the previous node when executing this node on demand
            previousTransformStepNode.executeIfNotAlready();
            super.executeIfNotAlready();
        }

        protected class TransformPreviousArtifacts extends AbstractTransformArtifacts {

            public TransformPreviousArtifacts(BuildOperationRunner buildOperationRunner) {
                super(buildOperationRunner);
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
        private final BuildOperationRunner buildOperationRunner;

        protected AbstractTransformArtifacts(BuildOperationRunner buildOperationRunner) {
            this.buildOperationRunner = buildOperationRunner;
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
            return (owningProject == null || !context.isPartOfExecutionGraph())
                ? buildOperation.transform()
                : buildOperationRunner.call(buildOperation);
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
