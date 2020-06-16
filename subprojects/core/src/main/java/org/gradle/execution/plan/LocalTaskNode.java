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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.resources.ResourceDeadlockException;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link TaskNode} implementation for a task in the current build.
 */
public class LocalTaskNode extends TaskNode {
    private final TaskInternal task;
    private final Map<File, String> canonicalizedFileCache;
    private ImmutableActionSet<Task> postAction = ImmutableActionSet.empty();
    private boolean isolated;
    private List<? extends ResourceLock> resourceLocks;

    public LocalTaskNode(TaskInternal task, Map<File, String> canonicalizedFileCache) {
        this.task = task;
        this.canonicalizedFileCache = canonicalizedFileCache;
    }

    /**
     * Indicates that this task is isolated and so does not require the project lock in order to execute.
     */
    public void isolated() {
        isolated = true;
    }

    @Nullable
    @Override
    public ResourceLock getProjectToLock() {
        if (isolated) {
            return null;
        } else {
            // Running the task requires access to the task's owning project
            return ((ProjectInternal) task.getProject()).getMutationState().getAccessLock();
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
    public Action<? super Task> getPostAction() {
        return postAction;
    }

    @Override
    public void appendPostAction(Action<? super Task> action) {
        postAction = postAction.add(action);
    }

    @Override
    public Throwable getNodeFailure() {
        return task.getState().getFailure();
    }

    @Override
    public void rethrowNodeFailure() {
        task.getState().rethrowFailure();
    }

    @Override
    public void prepareForExecution() {
        ((TaskContainerInternal) task.getProject().getTasks()).prepareForExecution(task);
    }

    @Override
    public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<Node> processHardSuccessor) {
        for (Node targetNode : getDependencies(dependencyResolver)) {
            addDependencySuccessor(targetNode);
            processHardSuccessor.execute(targetNode);
        }
        for (Node targetNode : getFinalizedBy(dependencyResolver)) {
            if (!(targetNode instanceof TaskNode)) {
                throw new IllegalStateException("Only tasks can be finalizers: " + targetNode);
            }
            addFinalizerNode((TaskNode) targetNode);
            processHardSuccessor.execute(targetNode);
        }
        for (Node targetNode : getMustRunAfter(dependencyResolver)) {
            addMustSuccessor((TaskNode) targetNode);
        }
        for (Node targetNode : getShouldRunAfter(dependencyResolver)) {
            addShouldSuccessor(targetNode);
        }
    }

    @Override
    public boolean requiresMonitoring() {
        return false;
    }

    private void addFinalizerNode(TaskNode finalizerNode) {
        addFinalizer(finalizerNode);
        if (!finalizerNode.isInKnownState()) {
            finalizerNode.mustNotRun();
        }
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
        if (getClass() != other.getClass()) {
            return getClass().getName().compareTo(other.getClass().getName());
        }
        LocalTaskNode localTask = (LocalTaskNode) other;
        return task.compareTo(localTask.task);
    }

    @Override
    public String toString() {
        return task.getIdentityPath().toString();
    }

    @Override
    public void resolveMutations() {
        final LocalTaskNode taskNode = this;
        final TaskInternal task = getTask();
        final MutationInfo mutations = getMutationInfo();
        ProjectInternal project = (ProjectInternal) task.getProject();
        ServiceRegistry serviceRegistry = project.getServices();
        final FileCollectionFactory fileCollectionFactory = serviceRegistry.get(FileCollectionFactory.class);
        PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
        try {
            TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor.Adapter() {
                @Override
                public void visitOutputFileProperty(final String propertyName, boolean optional, final PropertyValue value, final OutputFilePropertyType filePropertyType) {
                    withDeadlockHandling(
                        taskNode,
                        "an output",
                        "output property '" + propertyName + "'",
                        () -> FileParameterUtils.resolveOutputFilePropertySpecs(
                            task.toString(),
                            propertyName,
                            value,
                            filePropertyType,
                            fileCollectionFactory,
                            true,
                            outputFilePropertySpec -> {
                                File outputLocation = outputFilePropertySpec.getOutputFile();
                                if (outputLocation != null) {
                                    mutations.outputPaths.add(canonicalizePath(outputLocation, canonicalizedFileCache));
                                }
                            }
                        )
                    );
                    mutations.hasOutputs = true;
                }

                @Override
                public void visitLocalStateProperty(final Object value) {
                    withDeadlockHandling(
                        taskNode,
                        "a local state", "local state properties",
                        () -> mutations.outputPaths.addAll(canonicalizedPaths(canonicalizedFileCache, fileCollectionFactory.resolving(value))));
                    mutations.hasLocalState = true;
                }

                @Override
                public void visitDestroyableProperty(final Object value) {
                    withDeadlockHandling(
                        taskNode,
                        "a destroyable",
                        "destroyables",
                        () -> mutations.destroyablePaths.addAll(canonicalizedPaths(canonicalizedFileCache, fileCollectionFactory.resolving(value))));
                }

                @Override
                public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                    mutations.hasFileInputs = true;
                }
            });
        } catch (Exception e) {
            throw new TaskExecutionException(task, e);
        }

        mutations.resolved = true;

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

    private static ImmutableSet<String> canonicalizedPaths(final Map<File, String> cache, Iterable<File> files) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (File file : files) {
            builder.add(canonicalizePath(file, cache));
        }
        return builder.build();
    }

    private static String canonicalizePath(File file, Map<File, String> cache) {
        try {
            String path = cache.get(file);
            if (path == null) {
                path = file.getCanonicalPath();
                cache.put(file, path);
            }
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void withDeadlockHandling(TaskNode task, String singular, String description, Runnable runnable) {
        try {
            runnable.run();
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(String.format("A deadlock was detected while resolving the %s for task '%s'. This can be caused, for instance, by %s property causing dependency resolution.", description, task, singular), e);
        }
    }
}
