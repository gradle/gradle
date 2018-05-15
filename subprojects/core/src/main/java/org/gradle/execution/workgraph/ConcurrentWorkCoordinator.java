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

package org.gradle.execution.workgraph;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskProperties;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.internal.Pair;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.resources.ResourceDeadlockException;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.scheduler.ConcurrentNodeExecutionCoordinator;
import org.gradle.internal.scheduler.Edge;
import org.gradle.internal.scheduler.Graph;
import org.gradle.internal.scheduler.Node;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ConcurrentWorkCoordinator implements ConcurrentNodeExecutionCoordinator {
    private final Map<File, String> canonicalizedFileCache = Maps.newIdentityHashMap();
    private final Map<Pair<Node, Node>, Boolean> reachableCache = Maps.newHashMap();
    private final Map<Project, ResourceLock> projectLocks = Maps.newHashMap();
    private final WorkerLeaseService workerLeaseService;
    private final Map<TaskNode, TaskMutation> taskMutations = Maps.newHashMap();

    public ConcurrentWorkCoordinator(WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
    }

    @Nullable
    @Override
    public ResourceLock findLockFor(Node node) {
        if (node instanceof TaskNode) {
            Project project = ((TaskNode) node).getTask().getProject();
            ResourceLock projectLock = projectLocks.get(project);
            if (projectLock == null) {
                String gradlePath = ((GradleInternal) project.getGradle()).getIdentityPath().toString();
                String projectPath = ((ProjectInternal) project).getIdentityPath().toString();
                projectLock = workerLeaseService.getProjectLock(gradlePath, projectPath);
                projectLocks.put(project, projectLock);
            }
            return projectLock;
        }
        return null;
    }

    @Nullable
    @Override
    public Node findConflictingNode(Graph graph, Node nodeToRun, Collection<? extends Node> runningNodes) {
        if (runningNodes.isEmpty()) {
            return null;
        }
        if (!(nodeToRun instanceof TaskNode)) {
            return null;
        }
        // TODO Actually detect conflicts
        return null;
//        TaskNode taskNodeToRun = (TaskNode) nodeToRun;
//        TaskMutation taskMutation = getResolvedTaskMutation(taskNodeToRun);
//
//        return findConflictingNode(graph, taskNodeToRun, taskMutation, runningNodes);
    }

    @Nullable
    private Node findConflictingNode(Graph graph, TaskNode taskNode, TaskMutation taskMutation, Collection<? extends Node> runningNodes) {
        Set<String> candidateTaskMutations = !taskMutation.outputPaths.isEmpty() ? taskMutation.outputPaths : taskMutation.destroyablePaths;
        Node overlappingNode = findFirstTaskWithOverlappingMutations(candidateTaskMutations, runningNodes);
        if (overlappingNode != null) {
            return overlappingNode;
        }

        return findTaskUsingNotYetConsumedOutputOfAnotherTask(graph, taskNode, taskMutation.destroyablePaths);
    }

    @Nullable
    private TaskNode findTaskUsingNotYetConsumedOutputOfAnotherTask(Graph graph, TaskNode destroyerTask, Set<String> destroyablePaths) {
        if (!destroyablePaths.isEmpty()) {
            for (TaskMutation producingTask : taskMutations.values()) {
                if (producingTask.consumingTasks.isEmpty()) {
                    // We don't care about tasks whose output is not consumed by anyone anymore
                    continue;
                }
                if (!graph.containsNode(producingTask.task)) {
                    // We don't care about producing tasks that haven't finished yet
                    continue;
                }
                if (!hasOverlap(destroyablePaths, producingTask.outputPaths)) {
                    // No overlap no cry
                    continue;
                }
                for (TaskNode consumingTask : producingTask.consumingTasks) {
                    if (doesConsumerDependOnDestroyer(graph, consumingTask, destroyerTask)) {
                        // If there's an explicit dependency from consuming task to destroyer,
                        // then we accept that as the will of the user
                        continue;
                    }
                    return consumingTask;
                }
            }
        }
        return null;
    }

    private boolean doesConsumerDependOnDestroyer(Graph graph, Node consumer, Node destroyer) {
        if (consumer == destroyer) {
            return true;
        }
        Pair<Node, Node> nodePair = Pair.of(consumer, destroyer);
        if (reachableCache.get(nodePair) != null) {
            return reachableCache.get(nodePair);
        }

        boolean reachable = false;
        for (Edge incomingEdge : graph.getIncomingEdges(consumer)) {
            switch (incomingEdge.getType()) {
                case DEPENDENCY_OF:
                case FINALIZED_BY:
                case MUST_COMPLETE_BEFORE:
                    Node source = incomingEdge.getSource();
                    if (doesConsumerDependOnDestroyer(graph, source, destroyer)) {
                        reachable = true;
                    }
                    break;
                default:
                    break;
            }
        }
        reachableCache.put(nodePair, reachable);
        return reachable;
    }

    @Nullable
    private Node findFirstTaskWithOverlappingMutations(Set<String> candidateTaskMutations, Collection<? extends Node> runningNodes) {
        if (!candidateTaskMutations.isEmpty()) {
            for (Node runningNode : runningNodes) {
                if (!(runningNode instanceof TaskNode)) {
                    continue;
                }
                TaskMutation taskMutation = taskMutations.get(runningNode);
                Iterable<String> runningTaskMutations = Iterables.concat(taskMutation.outputPaths, taskMutation.destroyablePaths);
                if (hasOverlap(candidateTaskMutations, runningTaskMutations)) {
                    return runningNode;
                }
            }
        }
        return null;
    }

    private static boolean hasOverlap(Iterable<String> paths1, Iterable<String> paths2) {
        for (String path1 : paths1) {
            for (String path2 : paths2) {
                String overLappedPath = getOverLappedPath(path1, path2);
                if (overLappedPath != null) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
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

    private TaskMutation getResolvedTaskMutation(TaskNode taskNode) {
        TaskInternal task = taskNode.getTask();
        TaskMutation taskMutationInfo = taskMutations.get(taskNode);
        if (!taskMutationInfo.resolved) {
            ProjectInternal project = (ProjectInternal) task.getProject();
            ServiceRegistry serviceRegistry = project.getServices();
            PathToFileResolver resolver = serviceRegistry.get(PathToFileResolver.class);
            PropertyWalker propertyWalker = serviceRegistry.get(PropertyWalker.class);
            TaskProperties taskProperties = DefaultTaskProperties.resolve(propertyWalker, resolver, task);
            taskMutationInfo.outputPaths.addAll(getOutputPaths(canonicalizedFileCache, task, taskProperties.getOutputFiles(), taskProperties.getLocalStateFiles()));
            taskMutationInfo.destroyablePaths.addAll(getDestroyablePaths(canonicalizedFileCache, task, taskProperties.getDestroyableFiles()));
            taskMutationInfo.hasFileInputs = !taskProperties.getInputFileProperties().isEmpty();
            taskMutationInfo.hasOutputs = taskProperties.hasDeclaredOutputs();
            taskMutationInfo.hasLocalState = !taskProperties.getLocalStateFiles().isEmpty();
            taskMutationInfo.resolved = true;

            if (!taskMutationInfo.destroyablePaths.isEmpty()) {
                if (taskMutationInfo.hasOutputs) {
                    throw new IllegalStateException("Task " + task.getIdentityPath() + " has both outputs and destroyables defined. A task can define either outputs or destroyables, but not both.");
                }
                if (taskMutationInfo.hasFileInputs) {
                    throw new IllegalStateException("Task " + task.getIdentityPath() + " has both inputs and destroyables defined. A task can define either inputs or destroyables, but not both.");
                }
                if (taskMutationInfo.hasLocalState) {
                    throw new IllegalStateException("Task " + task.getIdentityPath() + " has both local state and destroyables defined. A task can define either local state or destroyables, but not both.");
                }
            }
        }
        return taskMutationInfo;
    }

    private static Set<String> getOutputPaths(Map<File, String> canonicalizedFileCache, Task task, FileCollection outputFiles, FileCollection localStateFiles) {
        try {
            return canonicalizedPaths(canonicalizedFileCache, Iterables.concat(outputFiles, localStateFiles));
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(deadlockMessage(task, "an output or local state", "outputs"), e);
        }
    }

    private static Set<String> getDestroyablePaths(Map<File, String> canonicalizedFileCache, Task task, FileCollection destroyableFiles) {
        try {
            return canonicalizedPaths(canonicalizedFileCache, destroyableFiles);
        } catch (ResourceDeadlockException e) {
            throw new IllegalStateException(deadlockMessage(task, "a destroyable", "destroyables"), e);
        }
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

    private static String deadlockMessage(Task task, String singular, String plural) {
        return String.format("A deadlock was detected while resolving the %s for task '%s'. This can be caused, for instance, by %s property causing dependency resolution.", plural, task, singular);
    }

    private static class TaskMutation {
        private final TaskNode task;
        private final Set<TaskNode> consumingTasks = Sets.newHashSet();
        private final Set<TaskNode> consumesOutputOf = Sets.newHashSet();
        private final Set<String> outputPaths = Sets.newHashSet();
        private final Set<String> destroyablePaths = Sets.newHashSet();
        private boolean hasFileInputs;
        private boolean hasOutputs;
        private boolean hasLocalState;
        private boolean resolved;

        public TaskMutation(TaskNode task) {
            this.task = task;
        }
    }
}
