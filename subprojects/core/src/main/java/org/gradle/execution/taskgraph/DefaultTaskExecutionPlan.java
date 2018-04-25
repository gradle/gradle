/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskgraph;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskProperties;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.resources.ResourceDeadlockException;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FAILED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.RETRY;

/**
 * A reusable implementation of TaskExecutionPlan. The {@link #clear()} methods are NOT threadsafe, and callers must synchronize access to these
 * methods.
 */
public class DefaultTaskExecutionPlan implements TaskExecutionPlan {
    private final List<TaskInfo> executionQueue = new LinkedList<TaskInfo>();
    private final TaskFailureCollector failureCollector;
    private final WorkExecutionPlan workExecutionPlan;
    private final Map<Project, ResourceLock> projectLocks = Maps.newHashMap();
    private final WorkerLeaseService workerLeaseService;

    private TaskFailureHandler failureHandler = new RethrowingFailureHandler();

    private final BuildCancellationToken cancellationToken;
    private final Set<TaskInfo> runningTasks = Sets.newIdentityHashSet();
    private final Map<TaskInfo, TaskMutationInfo> taskMutations = Maps.newIdentityHashMap();
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();
    private final Map<Pair<TaskInfo, TaskInfo>, Boolean> reachableCache = Maps.newHashMap();
    private final Set<TaskInfo> dependenciesCompleteCache = Sets.newHashSet();
    private final ResourceLockCoordinationService coordinationService;
    private final GradleInternal gradle;

    private boolean tasksCancelled;

    public DefaultTaskExecutionPlan(WorkGraph workGraph, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService, WorkerLeaseService workerLeaseService, GradleInternal gradle, TaskFailureCollector failureCollector) {
        this.cancellationToken = cancellationToken;
        this.coordinationService = coordinationService;
        this.workerLeaseService = workerLeaseService;
        this.workExecutionPlan = new WorkExecutionPlan(workGraph);
        this.gradle = gradle;
        this.failureCollector = failureCollector;
    }

    @Override
    public String getDisplayName() {
        Path path = gradle.findIdentityPath();
        if (path == null) {
            return "gradle";
        }
        return path.toString();
    }

    public void determineExecutionPlan() {
        workExecutionPlan.determineExecutionPlan();
        executionQueue.clear();
        Collection<TaskInfo> executionPlan = workExecutionPlan.getExecutionPlan();
        for (TaskInfo taskNode : executionPlan) {
            executionQueue.add(taskNode);
            createTaskMutationInfo(taskNode);
            Project project = taskNode.getTask().getProject();
            projectLocks.put(project, getOrCreateProjectLock(project));
        }
    }

    private void createTaskMutationInfo(TaskInfo taskNode) {
        TaskMutationInfo taskMutationInfo = getOrCreateMutationsOf(taskNode, taskMutations);

        for (TaskInfo dependency : taskNode.getDependencySuccessors()) {
            getOrCreateMutationsOf(dependency, taskMutations).consumingTasks.add(taskNode);
            taskMutationInfo.consumesOutputOf.add(dependency);
        }
    }

    private TaskMutationInfo getOrCreateMutationsOf(TaskInfo taskInfo, Map<TaskInfo, TaskMutationInfo> taskMutations) {
        TaskMutationInfo mutations = taskMutations.get(taskInfo);
        if (mutations == null) {
            mutations = new TaskMutationInfo(taskInfo);
            taskMutations.put(taskInfo, mutations);
        }
        return mutations;
    }


    private ResourceLock getOrCreateProjectLock(Project project) {
        String gradlePath = ((GradleInternal) project.getGradle()).getIdentityPath().toString();
        String projectPath = ((ProjectInternal) project).getIdentityPath().toString();
        return workerLeaseService.getProjectLock(gradlePath, projectPath);
    }

    private ResourceLock getProjectLock(TaskInfo taskInfo) {
        return projectLocks.get(taskInfo.getTask().getProject());
    }

    public boolean allProjectsLocked() {
        for (ResourceLock lock : projectLocks.values()) {
            if (!lock.isLocked()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<Task> getDependencies(Task task) {
        return workExecutionPlan.getDependencies(task);
    }

    public void clear() {
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                workExecutionPlan.clear();
                projectLocks.clear();
                taskMutations.clear();
                executionQueue.clear();
                failureCollector.clearFailures();
                taskMutations.clear();
                canonicalizedFileCache.clear();
                reachableCache.clear();
                dependenciesCompleteCache.clear();
                runningTasks.clear();
                return FINISHED;
            }
        });
    }

    @Override
    public List<Task> getTasks() {
        return workExecutionPlan.getTasks();
    }

    public void useFailureHandler(TaskFailureHandler handler) {
        this.failureHandler = handler;
    }

    @Override
    public boolean executeWithTask(final WorkerLease workerLease, final Action<TaskInternal> taskExecution) {
        final AtomicReference<TaskInfo> selected = new AtomicReference<TaskInfo>();
        final AtomicBoolean workRemaining = new AtomicBoolean();
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (cancellationToken.isCancellationRequested()) {
                    if (abortExecution()) {
                        tasksCancelled = true;
                    }
                }

                workRemaining.set(workRemaining());
                if (!workRemaining.get()) {
                    return FINISHED;
                }

                if (allProjectsLocked()) {
                    return RETRY;
                }

                try {
                    selected.set(selectNextTask(workerLease));
                } catch (Throwable t) {
                    abortAllAndFail(t);
                    workRemaining.set(false);
                }

                if (selected.get() == null && workRemaining.get()) {
                    return RETRY;
                } else {
                    return FINISHED;
                }
            }
        });

        TaskInfo selectedTask = selected.get();
        execute(selectedTask, workerLease, taskExecution);
        return workRemaining.get();
    }

    private TaskInfo selectNextTask(final WorkerLease workerLease) {
        final AtomicReference<TaskInfo> selected = new AtomicReference<TaskInfo>();
        final Iterator<TaskInfo> iterator = executionQueue.iterator();
        while (iterator.hasNext()) {
            final TaskInfo taskInfo = iterator.next();
            if (taskInfo.isReady() && allDependenciesComplete(taskInfo)) {
                coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                    @Override
                    public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                        ResourceLock projectLock = getProjectLock(taskInfo);
                        TaskMutationInfo taskMutationInfo = getResolvedTaskMutationInfo(taskInfo);

                        // TODO: convert output file checks to a resource lock
                        if (!projectLock.tryLock() || !workerLease.tryLock() || !canRunWithCurrentlyExecutedTasks(taskInfo, taskMutationInfo)) {
                            return FAILED;
                        }

                        selected.set(taskInfo);
                        if (taskInfo.allDependenciesSuccessful()) {
                            recordTaskStarted(taskInfo);
                            taskInfo.startExecution();
                        } else {
                            taskInfo.skipExecution();
                        }
                        iterator.remove();
                        return FINISHED;
                    }
                });

                if (selected.get() != null) {
                    break;
                }
            }
        }
        return selected.get();
    }

    private TaskMutationInfo getResolvedTaskMutationInfo(TaskInfo taskInfo) {
        TaskInternal task = taskInfo.getTask();
        TaskMutationInfo taskMutationInfo = taskMutations.get(taskInfo);
        if (!taskMutationInfo.resolved) {
            ProjectInternal project = (ProjectInternal) task.getProject();
            ServiceRegistry serviceRegistry = project.getServices();
            PathToFileResolver resolver = serviceRegistry.get(PathToFileResolver.class);
            PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
            TaskProperties taskProperties = DefaultTaskProperties.resolve(propertyWalker, resolver, task);
            taskMutationInfo.outputPaths.addAll(getOutputPaths(canonicalizedFileCache, taskInfo, taskProperties.getOutputFiles(), taskProperties.getLocalStateFiles()));
            taskMutationInfo.destroyablePaths.addAll(getDestroyablePaths(canonicalizedFileCache, taskInfo, taskProperties.getDestroyableFiles()));
            taskMutationInfo.hasFileInputs = !taskProperties.getInputFileProperties().isEmpty();
            taskMutationInfo.hasOutputs = taskProperties.hasDeclaredOutputs();
            taskMutationInfo.hasLocalState = !taskProperties.getLocalStateFiles().isEmpty();
            taskMutationInfo.resolved = true;
        }
        return taskMutationInfo;
    }

    private void execute(final TaskInfo selectedTask, final WorkerLease workerLease, Action<TaskInternal> taskExecution) {
        if (selectedTask == null) {
            return;
        }
        try {
            if (!selectedTask.isComplete()) {
                try {
                    taskExecution.execute(selectedTask.getTask());
                } catch (Throwable e) {
                    selectedTask.setExecutionFailure(e);
                }
            }
        } finally {
            coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                @Override
                public ResourceLockState.Disposition transform(ResourceLockState state) {
                    if (!selectedTask.isComplete()) {
                        taskComplete(selectedTask);
                    }
                    return unlock(workerLease, getProjectLock(selectedTask)).transform(state);
                }
            });
        }
    }

    private boolean allDependenciesComplete(TaskInfo taskInfo) {
        if (dependenciesCompleteCache.contains(taskInfo)) {
            return true;
        }

        boolean dependenciesComplete = taskInfo.allDependenciesComplete();
        if (dependenciesComplete) {
            dependenciesCompleteCache.add(taskInfo);
        }

        return dependenciesComplete;
    }

    private boolean canRunWithCurrentlyExecutedTasks(TaskInfo taskInfo, TaskMutationInfo taskMutationInfo) {
        Set<String> candidateTaskDestroyables = taskMutationInfo.destroyablePaths;

        if (!candidateTaskDestroyables.isEmpty()) {
            if (taskMutationInfo.hasOutputs) {
                throw new IllegalStateException("Task " + taskInfo.getTask().getIdentityPath() + " has both outputs and destroyables defined.  A task can define either outputs or destroyables, but not both.");
            }
            if (taskMutationInfo.hasFileInputs) {
                throw new IllegalStateException("Task " + taskInfo.getTask().getIdentityPath() + " has both inputs and destroyables defined.  A task can define either inputs or destroyables, but not both.");
            }
            if (taskMutationInfo.hasLocalState) {
                throw new IllegalStateException("Task " + taskInfo.getTask().getIdentityPath() + " has both local state and destroyables defined.  A task can define either local state or destroyables, but not both.");
            }
        }

        if (!runningTasks.isEmpty()) {
            Set<String> candidateTaskOutputs = taskMutationInfo.outputPaths;
            Set<String> candidateTaskMutations = !candidateTaskOutputs.isEmpty() ? candidateTaskOutputs : candidateTaskDestroyables;
            Pair<TaskInfo, String> overlap = firstRunningTaskWithOverlappingMutations(candidateTaskMutations);
            if (overlap != null) {
                return false;
            }
        }

        Pair<TaskInfo, String> overlap = firstTaskWithDestroyedIntermediateInput(taskInfo, candidateTaskDestroyables);
        if (overlap != null) {
            return false;
        }

        return true;
    }

    private static ImmutableSet<String> canonicalizedPaths(final Map<File, String> cache, Iterable<File> files) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (File file : files) {
            String path;
            try {
                path = cache.get(file);
                if (path == null) {
                    path = file.getCanonicalPath();
                    cache.put(file, path);
                }
                builder.add(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return builder.build();
    }

    @Nullable
    private Pair<TaskInfo, String> firstRunningTaskWithOverlappingMutations(Set<String> candidateTaskMutations) {
        if (!candidateTaskMutations.isEmpty()) {
            for (TaskInfo runningTask : runningTasks) {
                TaskMutationInfo taskMutationInfo = taskMutations.get(runningTask);
                Iterable<String> runningTaskMutations = Iterables.concat(taskMutationInfo.outputPaths, taskMutationInfo.destroyablePaths);
                String firstOverlap = findFirstOverlap(candidateTaskMutations, runningTaskMutations);
                if (firstOverlap != null) {
                    return Pair.of(runningTask, firstOverlap);
                }
            }
        }

        return null;
    }

    @Nullable
    private Pair<TaskInfo, String> firstTaskWithDestroyedIntermediateInput(final TaskInfo taskInfo, Set<String> destroyablePaths) {
        if (!destroyablePaths.isEmpty()) {
            Iterator<TaskMutationInfo> iterator = taskMutations.values().iterator();
            while (iterator.hasNext()) {
                TaskMutationInfo taskMutationInfo = iterator.next();
                if (taskMutationInfo.task.isComplete() && !taskMutationInfo.consumingTasks.isEmpty()) {
                    String firstOverlap = findFirstOverlap(destroyablePaths, taskMutationInfo.outputPaths);
                    if (firstOverlap != null) {
                        for (TaskInfo consumingTask : taskMutationInfo.consumingTasks) {
                            if (consumingTask != taskInfo && !isReachableFrom(consumingTask, taskInfo)) {
                                return Pair.of(consumingTask, firstOverlap);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isReachableFrom(TaskInfo fromTask, TaskInfo toTask) {
        Pair<TaskInfo, TaskInfo> taskPair = Pair.of(fromTask, toTask);
        if (reachableCache.get(taskPair) != null) {
            return reachableCache.get(taskPair);
        }

        boolean reachable = false;
        for (TaskInfo dependency : Iterables.concat(fromTask.getMustSuccessors(), fromTask.getDependencySuccessors())) {
            if (!dependency.isComplete()) {
                if (dependency == toTask) {
                    reachable = true;
                }
                if (isReachableFrom(dependency, toTask)) {
                    reachable = true;
                }
            }
        }

        reachableCache.put(taskPair, reachable);
        return reachable;
    }

    private static String findFirstOverlap(Iterable<String> paths1, Iterable<String> paths2) {
        for (String path1 : paths1) {
            for (String path2 : paths2) {
                String overLappedPath = getOverLappedPath(path1, path2);
                if (overLappedPath != null) {
                    return overLappedPath;
                }
            }
        }

        return null;
    }

    private static Set<String> getOutputPaths(Map<File, String> canonicalizedFileCache, TaskInfo task, FileCollection outputFiles, FileCollection localStateFiles) {
        try {
            return canonicalizedPaths(canonicalizedFileCache, Iterables.concat(outputFiles, localStateFiles));
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(deadlockMessage(task, "an output or local state", "outputs"), e);
        }
    }

    private static Set<String> getDestroyablePaths(Map<File, String> canonicalizedFileCache, TaskInfo task, FileCollection destroyableFiles) {
        try {
            return canonicalizedPaths(canonicalizedFileCache, destroyableFiles);
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(deadlockMessage(task, "a destroyable", "destroyables"), e);
        }
    }

    private static String deadlockMessage(TaskInfo task, String singular, String plural) {
        return String.format("A deadlock was detected while resolving the %s for task '%s'. This can be caused, for instance, by %s property causing dependency resolution.", plural, task, singular);
    }

    private static String getOverLappedPath(String firstPath, String secondPath) {
        if (firstPath.equals(secondPath)) {
            return firstPath;
        }
        if (firstPath.length() == secondPath.length()) {
            return null;
        }

        String shorter;
        String longer;
        if (firstPath.length() > secondPath.length()) {
            shorter = secondPath;
            longer = firstPath;
        } else {
            shorter = firstPath;
            longer = secondPath;
        }

        boolean isOverlapping = longer.startsWith(shorter) && longer.charAt(shorter.length()) == File.separatorChar;
        if (isOverlapping) {
            return shorter;
        } else {
            return null;
        }
    }

    private void recordTaskStarted(TaskInfo taskInfo) {
        runningTasks.add(taskInfo);
    }

    private void recordTaskCompleted(TaskInfo taskInfo) {
        runningTasks.remove(taskInfo);
        TaskMutationInfo taskMutationInfo = taskMutations.get(taskInfo);
        for (TaskInfo producerTask : taskMutationInfo.consumesOutputOf) {
            TaskMutationInfo producerTaskMutationInfo = taskMutations.get(producerTask);
            if (producerTaskMutationInfo.consumingTasks.remove(taskInfo) && canRemoveTaskMutation(producerTaskMutationInfo)) {
                taskMutations.remove(producerTask);
            }
        }

        if (canRemoveTaskMutation(taskMutationInfo)) {
            taskMutations.remove(taskInfo);
        }
    }

    private static boolean canRemoveTaskMutation(TaskMutationInfo taskMutationInfo) {
        return taskMutationInfo != null && taskMutationInfo.task.isComplete() && taskMutationInfo.consumingTasks.isEmpty();
    }

    private void taskComplete(TaskInfo taskInfo) {
        enforceFinalizerTasks(taskInfo);
        if (taskInfo.isFailed()) {
            handleFailure(taskInfo);
        }

        taskInfo.finishExecution();
        recordTaskCompleted(taskInfo);
    }

    private static void enforceFinalizerTasks(TaskInfo taskInfo) {
        for (TaskInfo finalizerNode : taskInfo.getFinalizers()) {
            if (finalizerNode.isRequired() || finalizerNode.isMustNotRun()) {
                enforceWithDependencies(finalizerNode, Sets.<TaskInfo>newHashSet());
            }
        }
    }

    private static void enforceWithDependencies(TaskInfo nodeInfo, Set<TaskInfo> enforcedTasks) {
        Deque<TaskInfo> candidateNodes = new ArrayDeque<TaskInfo>();
        candidateNodes.add(nodeInfo);

        while (!candidateNodes.isEmpty()) {
            TaskInfo node = candidateNodes.pop();
            if (!enforcedTasks.contains(node)) {
                enforcedTasks.add(node);

                candidateNodes.addAll(node.getDependencySuccessors());

                if (node.isMustNotRun() || node.isRequired()) {
                    node.enforceRun();
                }
            }
        }
    }

    private void abortAllAndFail(Throwable t) {
        abortExecution(true);
        this.failureCollector.addFailure(t);
    }

    private void handleFailure(TaskInfo taskInfo) {
        Throwable executionFailure = taskInfo.getExecutionFailure();
        if (executionFailure != null) {
            // Always abort execution for an execution failure (as opposed to a task failure)
            abortExecution();
            this.failureCollector.addFailure(executionFailure);
            return;
        }

        // Task failure
        try {
            failureHandler.onTaskFailure(taskInfo.getTask());
            this.failureCollector.addFailure(taskInfo.getTaskFailure());
        } catch (Exception e) {
            // If the failure handler rethrows exception, then execution of other tasks is aborted. (--continue will collect failures)
            abortExecution();
            this.failureCollector.addFailure(e);
        }
    }

    private boolean abortExecution() {
        return abortExecution(false);
    }

    private boolean abortExecution(boolean abortAll) {
        boolean aborted = false;
        for (TaskInfo taskInfo : workExecutionPlan.getExecutionPlan()) {
            // Allow currently executing and enforced tasks to complete, but skip everything else.
            if (taskInfo.isRequired()) {
                taskInfo.skipExecution();
                aborted = true;
            }

            // If abortAll is set, also stop enforced tasks.
            if (abortAll && taskInfo.isReady()) {
                taskInfo.abortExecution();
                aborted = true;
            }
        }
        return aborted;
    }

    public void awaitCompletion() {
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (allTasksComplete()) {
                    rethrowFailures();
                    return FINISHED;
                } else {
                    return RETRY;
                }
            }
        });
    }

    private void rethrowFailures() {
        if (tasksCancelled) {
            failureCollector.addFailure(new BuildCancelledException());
        }
        if (failureCollector.getFailures().isEmpty()) {
            return;
        }

        if (failureCollector.getFailures().size() > 1) {
            throw new MultipleBuildFailures(failureCollector.getFailures());
        }

        throw UncheckedException.throwAsUncheckedException(failureCollector.getFailures().get(0));
    }

    private boolean allTasksComplete() {
        for (TaskInfo taskInfo : workExecutionPlan.getExecutionPlan()) {
            if (!taskInfo.isComplete()) {
                return false;
            }
        }
        return true;
    }

    private boolean workRemaining() {
        for (TaskInfo taskInfo : executionQueue) {
            if (!taskInfo.isComplete()) {
                return true;
            }
        }
        return false;
    }

    private static class RethrowingFailureHandler implements TaskFailureHandler {
        public void onTaskFailure(Task task) {
            task.getState().rethrowFailure();
        }
    }

    private static class TaskMutationInfo {
        final TaskInfo task;
        final Set<TaskInfo> consumingTasks = Sets.newHashSet();
        final Set<TaskInfo> consumesOutputOf = Sets.newHashSet();
        final Set<String> outputPaths = Sets.newHashSet();
        final Set<String> destroyablePaths = Sets.newHashSet();
        boolean hasFileInputs;
        boolean hasOutputs;
        boolean hasLocalState;
        boolean resolved;

        TaskMutationInfo(TaskInfo task) {
            this.task = task;
        }
    }
}
