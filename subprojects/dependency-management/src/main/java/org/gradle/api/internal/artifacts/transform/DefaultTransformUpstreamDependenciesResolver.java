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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.specs.Spec;
import org.gradle.execution.plan.PostExecutionNodeAwareActionNode;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.Try;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

public class DefaultTransformUpstreamDependenciesResolver implements TransformUpstreamDependenciesResolver {
    public static final ArtifactTransformDependencies NO_RESULT = new ArtifactTransformDependencies() {
        @Override
        public Optional<FileCollection> getFiles() {
            return Optional.empty();
        }
    };
    public static final TransformUpstreamDependencies NO_DEPENDENCIES = new TransformUpstreamDependencies() {

        @Nullable
        @Override
        public ConfigurationIdentity getConfigurationIdentity() {
            return null;
        }

        @Override
        public FileCollection selectedArtifacts() {
            throw failure();
        }

        @Override
        public void finalizeIfNotAlready() {
        }

        @Override
        public Try<ArtifactTransformDependencies> computeArtifacts() {
            return Try.successful(NO_RESULT);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }
    };

    private final ComponentIdentifier componentIdentifier;
    private final ConfigurationIdentity configurationIdentity;
    private final ResolutionResultProvider<ResolutionResult> resolutionResultProvider;
    private final DomainObjectContext owner;
    private final FilteredResultFactory filteredResultFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private Set<ComponentIdentifier> buildDependencies;
    private Set<ComponentIdentifier> dependencies;

    public DefaultTransformUpstreamDependenciesResolver(
        ComponentIdentifier componentIdentifier,
        ConfigurationIdentity configurationIdentity,
        ResolutionResultProvider<ResolutionResult> resolutionResultProvider,
        DomainObjectContext owner,
        FilteredResultFactory filteredResultFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.componentIdentifier = componentIdentifier;
        this.configurationIdentity = configurationIdentity;
        this.resolutionResultProvider = resolutionResultProvider;
        this.owner = owner;
        this.filteredResultFactory = filteredResultFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("Transform does not use artifact dependencies.");
    }

    @Override
    public TransformUpstreamDependencies dependenciesFor(TransformationStep transformationStep) {
        if (!transformationStep.requiresDependencies()) {
            return NO_DEPENDENCIES;
        }
        return new TransformUpstreamDependenciesImpl(configurationIdentity, transformationStep, calculatedValueContainerFactory);
    }

    private FileCollectionInternal selectedArtifactsFor(ImmutableAttributes fromAttributes) {
        if (dependencies == null) {
            ResolutionResult result = resolutionResultProvider.getValue();
            dependencies = computeDependencies(componentIdentifier, ComponentIdentifier.class, result.getAllComponents(), false);
        }
        return filteredResultFactory.resultsMatching(fromAttributes, selectDependenciesWithId(dependencies));
    }

    private void computeDependenciesFor(ImmutableAttributes fromAttributes, TaskDependencyResolveContext context) {
        if (buildDependencies == null) {
            ResolutionResult result = resolutionResultProvider.getTaskDependencyValue();
            buildDependencies = computeDependencies(componentIdentifier, ComponentIdentifier.class, result.getAllComponents(), true);
        }
        FileCollectionInternal files = filteredResultFactory.resultsMatching(fromAttributes, selectDependenciesWithId(buildDependencies));
        context.add(files);
    }

    private Spec<ComponentIdentifier> selectDependenciesWithId(Set<ComponentIdentifier> dependencies) {
        return spec(element -> dependencies.contains(element));
    }

    private static Set<ComponentIdentifier> computeDependencies(ComponentIdentifier componentIdentifier, Class<? extends ComponentIdentifier> type, Set<ResolvedComponentResult> componentResults, boolean strict) {
        ResolvedComponentResult targetComponent = null;
        for (ResolvedComponentResult component : componentResults) {
            if (component.getId().equals(componentIdentifier)) {
                targetComponent = component;
                break;
            }
        }
        if (targetComponent == null) {
            if (strict) {
                throw new AssertionError("Could not find component " + componentIdentifier + " in provided results.");
            } else {
                return Collections.emptySet();
            }
        }
        Set<ComponentIdentifier> buildDependencies = new HashSet<>();
        collectDependenciesIdentifiers(buildDependencies, type, new HashSet<>(), targetComponent.getDependencies());
        return buildDependencies;
    }

    private static void collectDependenciesIdentifiers(Set<ComponentIdentifier> dependenciesIdentifiers, Class<? extends ComponentIdentifier> type, Set<ComponentIdentifier> visited, Set<? extends DependencyResult> dependencies) {
        for (DependencyResult dependency : dependencies) {
            if (dependency instanceof ResolvedDependencyResult && !dependency.isConstraint()) {
                ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                ResolvedComponentResult selected = resolvedDependency.getSelected();
                if (type.isInstance(selected.getId())) {
                    dependenciesIdentifiers.add(selected.getId());
                }
                if (visited.add(selected.getId())) {
                    // Do not traverse if seen already
                    collectDependenciesIdentifiers(dependenciesIdentifiers, type, visited, selected.getDependencies());
                }
            }
        }
    }

    /**
     * Represents a work node that prepares the upstream dependencies of a particular transform applied to a particular artifact.
     * This is a separate node so that this work can access project state to do the resolution and to discover additional dependencies for the transform
     * during resolution of upstream dependencies. It also allows the work of resolution to be attributed separately to the work of the transform.
     */
    public static abstract class FinalizeTransformDependencies implements ValueCalculator<ArtifactTransformDependencies> {
        public abstract FileCollection selectedArtifacts();

        @Override
        public ArtifactTransformDependencies calculateValue(NodeExecutionContext context) {
            FileCollection files = selectedArtifacts();
            // Trigger resolution, including any failures
            files.getFiles();
            return new DefaultArtifactTransformDependencies(files);
        }
    }

    /**
     * A work node used in builds where the upstream dependencies must be resolved. This implementation is not used when the work graph is loaded from the configuration cache,
     * as the dependencies have already been resolved in that case.
     */
    public class FinalizeTransformDependenciesFromSelectedArtifacts extends FinalizeTransformDependencies {
        private final ImmutableAttributes fromAttributes;

        public FinalizeTransformDependenciesFromSelectedArtifacts(ImmutableAttributes fromAttributes) {
            this.fromAttributes = fromAttributes;
        }

        @Override
        public FileCollectionInternal selectedArtifacts() {
            return selectedArtifactsFor(fromAttributes);
        }

        @Override
        public boolean usesMutableProjectState() {
            return owner.getProject() != null;
        }

        @Override
        public ProjectInternal getOwningProject() {
            return owner.getProject();
        }

        @Nullable
        @Override
        public WorkNodeAction getPreExecutionAction() {
            // Before resolving, need to determine the full set of upstream dependencies that need to be built.
            // The full set is usually known when the work graph is built. However, in certain cases where a project dependency conflicts with an external dependency, this is not known
            // until the full graph resolution, which can happen at execution time.
            return new CalculateFinalDependencies();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            computeDependenciesFor(fromAttributes, context);
        }

        public class CalculateFinalDependencies implements PostExecutionNodeAwareActionNode {
            final List<TaskNode> tasks = new ArrayList<>();

            @Override
            public boolean usesMutableProjectState() {
                return FinalizeTransformDependenciesFromSelectedArtifacts.this.usesMutableProjectState();
            }

            @Nullable
            @Override
            public Project getOwningProject() {
                return FinalizeTransformDependenciesFromSelectedArtifacts.this.getOwningProject();
            }

            @Override
            public void run(NodeExecutionContext context) {
                TaskNodeFactory taskNodeFactory = context.getService(TaskNodeFactory.class);
                selectedArtifacts().visitDependencies(new CollectingTaskDependencyResolveContext(tasks, taskNodeFactory));
            }

            @Override
            public List<TaskNode> getPostExecutionNodes() {
                return tasks;
            }

        }
    }

    private static class CollectingTaskDependencyResolveContext implements TaskDependencyResolveContext {
        private final TaskNodeFactory taskNodeFactory;
        private final Collection<TaskNode> tasks;

        public CollectingTaskDependencyResolveContext(Collection<TaskNode> tasks, TaskNodeFactory taskNodeFactory) {
            this.tasks = tasks;
            this.taskNodeFactory = taskNodeFactory;
        }

        @Override
        public void add(Object dependency) {
            if (dependency instanceof Task) {
                tasks.add(taskNodeFactory.getNode((Task) dependency));
            }
        }

        @Override
        public void visitFailure(Throwable failure) {
        }

        @Nullable
        @Override
        public Task getTask() {
            return null;
        }
    }

    private class TransformUpstreamDependenciesImpl implements TransformUpstreamDependencies {
        private final ConfigurationIdentity configurationIdentity;
        private final CalculatedValueContainer<ArtifactTransformDependencies, FinalizeTransformDependencies> transformDependencies;
        private final ImmutableAttributes fromAttributes;

        public TransformUpstreamDependenciesImpl(ConfigurationIdentity configurationIdentity, TransformationStep transformationStep, CalculatedValueContainerFactory calculatedValueContainerFactory) {
            this.configurationIdentity = configurationIdentity;
            this.fromAttributes = transformationStep.getFromAttributes();
            transformDependencies = calculatedValueContainerFactory.create(Describables.of("dependencies for", componentIdentifier, fromAttributes),
                new FinalizeTransformDependenciesFromSelectedArtifacts(transformationStep.getFromAttributes()));
        }

        @Override
        public ConfigurationIdentity getConfigurationIdentity() {
            return configurationIdentity;
        }

        @Override
        public FileCollection selectedArtifacts() {
            return selectedArtifactsFor(fromAttributes);
        }

        @Override
        public Try<ArtifactTransformDependencies> computeArtifacts() {
            return transformDependencies.getValue();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            context.add(transformDependencies);
        }

        @Override
        public void finalizeIfNotAlready() {
            transformDependencies.finalizeIfNotAlready();
        }
    }
}
