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

package org.gradle.execution.plan;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.properties.DefaultTaskProperties;
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * A {@link TaskNode} implementation for a task in the current build.
 */
public class LocalTaskNode extends TaskNode {
    private final TaskInternal task;
    private final WorkValidationContext validationContext;
    private final ResolveMutationsNode resolveMutationsNode;
    private Set<Node> lifecycleSuccessors;

    private boolean isolated;
    private List<? extends ResourceLock> resourceLocks;
    private TaskProperties taskProperties;

    public LocalTaskNode(TaskInternal task, NodeValidator nodeValidator, WorkValidationContext workValidationContext) {
        this.task = task;
        this.validationContext = workValidationContext;
        resolveMutationsNode = new ResolveMutationsNode(this, nodeValidator);
    }

    /**
     * Indicates that this task is isolated and so does not require the project lock in order to execute.
     */
    public void isolated() {
        isolated = true;
    }

    public WorkValidationContext getValidationContext() {
        return validationContext;
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        if (isolated) {
            return null;
        } else {
            // Running the task requires permission to execute against its containing project
            return ((ProjectInternal) task.getProject()).getOwner().getTaskExecutionLock();
        }
    }

    @Nullable
    @Override
    public ProjectInternal getOwningProject() {
        // Task requires its owning project's execution services
        return (ProjectInternal) task.getProject();
    }

    @Override
    public List<? extends ResourceLock> getResourcesToLock() {
        if (resourceLocks == null) {
            resourceLocks = task.getSharedResources();
        }
        return resourceLocks;
    }

    @Override
    public TaskInternal getTask() {
        return task;
    }

    @Override
    public boolean isPublicNode() {
        return true;
    }

    @Override
    public boolean isCanCancel() {
        FinalizerGroup finalizerGroup = getFinalizerGroup();
        if (finalizerGroup != null) {
            // TODO(mlopatkin) what if this node is some dependency of a finalizer and its group is a CompositeNodeGroup and not just a FinalizerGroup?
            for (Node node : finalizerGroup.getFinalizedNodes()) {
                // Cannot cancel this node if something it finalizes has started
                if (node.isExecuting() || node.isExecuted()) {
                    return false;
                }
            }
        }
        return true;
    }

    public TaskProperties getTaskProperties() {
        return taskProperties;
    }

    @Override
    public Throwable getNodeFailure() {
        return task.getState().getFailure();
    }

    @Override
    public void prepareForExecution(Action<Node> monitor) {
        ((TaskContainerInternal) task.getProject().getTasks()).prepareForExecution(task);
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver) {
        for (Node targetNode : getDependencies(dependencyResolver)) {
            addDependencySuccessor(targetNode);
        }

        lifecycleSuccessors = dependencyResolver.resolveDependenciesFor(task, task.getLifecycleDependencies());

        for (Node targetNode : getFinalizedBy(dependencyResolver)) {
            if (!(targetNode instanceof TaskNode)) {
                throw new IllegalStateException("Only tasks can be finalizers: " + targetNode);
            }
            addFinalizerNode((TaskNode) targetNode);
        }
        for (Node targetNode : getMustRunAfter(dependencyResolver)) {
            addMustSuccessor((TaskNode) targetNode);
        }
        for (Node targetNode : getShouldRunAfter(dependencyResolver)) {
            addShouldSuccessor(targetNode);
        }
    }

    private void addFinalizerNode(TaskNode finalizerNode) {
        deprecateLifecycleHookReferencingNonLocalTask("finalizedBy", finalizerNode);
        finalizerNode.addFinalizingSuccessor(this);
    }

    private Set<Node> getDependencies(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getTaskDependencies());
    }

    private Set<Node> getFinalizedBy(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getFinalizedBy());
    }

    private Set<Node> getMustRunAfter(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getMustRunAfter());
    }

    private Set<Node> getShouldRunAfter(TaskDependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependenciesFor(task, task.getShouldRunAfter());
    }

    @Override
    public int compareTo(Node other) {
        // Prefer to run tasks first
        if (!(other instanceof LocalTaskNode)) {
            return -1;
        }
        LocalTaskNode localTask = (LocalTaskNode) other;
        return task.compareTo(localTask.task);
    }

    @Override
    public String toString() {
        return task.getIdentityPath().toString();
    }

    private void addOutputFilesToMutations(Set<OutputFilePropertySpec> outputFilePropertySpecs) {
        final MutationInfo mutations = getMutationInfo();
        outputFilePropertySpecs.forEach(spec -> {
            File outputLocation = spec.getOutputFile();
            if (outputLocation != null) {
                mutations.outputPaths.add(outputLocation.getAbsolutePath());
            }
            mutations.hasOutputs = true;
        });
    }

    private void addLocalStateFilesToMutations(FileCollection localStateFiles) {
        final MutationInfo mutations = getMutationInfo();
        localStateFiles.forEach(file -> {
            mutations.outputPaths.add(file.getAbsolutePath());
            mutations.hasLocalState = true;
        });
    }

    private void addDestroyablesToMutations(FileCollection destroyables) {
        destroyables
            .forEach(file -> getMutationInfo().destroyablePaths.add(file.getAbsolutePath()));
    }

    @Override
    public Node getPrepareNode() {
        return resolveMutationsNode;
    }

    public void resolveMutations() {
        final LocalTaskNode taskNode = this;
        final TaskInternal task = getTask();
        final MutationInfo mutations = getMutationInfo();
        ProjectInternal project = (ProjectInternal) task.getProject();
        ServiceRegistry serviceRegistry = project.getServices();
        final FileCollectionFactory fileCollectionFactory = serviceRegistry.get(FileCollectionFactory.class);
        PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
        try {
            taskProperties = DefaultTaskProperties.resolve(propertyWalker, fileCollectionFactory, task);

            addOutputFilesToMutations(taskProperties.getOutputFileProperties());
            addLocalStateFilesToMutations(taskProperties.getLocalStateFiles());
            addDestroyablesToMutations(taskProperties.getDestroyableFiles());

            mutations.hasFileInputs = !taskProperties.getInputFileProperties().isEmpty();
        } catch (Exception e) {
            throw new TaskExecutionException(task, e);
        }

        if (!mutations.destroyablePaths.isEmpty()) {
            if (mutations.hasOutputs) {
                throw new IllegalStateException("Task " + taskNode + " has both outputs and destroyables defined.  A task can define either outputs or destroyables, but not both.");
            }
            if (mutations.hasFileInputs) {
                throw new IllegalStateException("Task " + taskNode + " has both inputs and destroyables defined.  A task can define either inputs or destroyables, but not both.");
            }
            if (mutations.hasLocalState) {
                throw new IllegalStateException("Task " + taskNode + " has both local state and destroyables defined.  A task can define either local state or destroyables, but not both.");
            }
        }
    }

    @Override
    public Set<Node> getLifecycleSuccessors() {
        return lifecycleSuccessors;
    }

    @Override
    public void setLifecycleSuccessors(Set<Node> lifecycleSuccessors) {
        this.lifecycleSuccessors = lifecycleSuccessors;
    }

    /**
     * Used to determine whether a {@link Node} consumes the <b>outcome</b> of a successor task vs. its output(s).
     *
     * @param dependency a non-successful successor node in the execution plan
     * @return true if the successor node dependency was declared with an explicit dependsOn relationship, false otherwise (implying task output -> task input relationship)
     */
    @Override
    protected boolean dependsOnOutcome(Node dependency) {
        return lifecycleSuccessors.contains(dependency);
    }
}
