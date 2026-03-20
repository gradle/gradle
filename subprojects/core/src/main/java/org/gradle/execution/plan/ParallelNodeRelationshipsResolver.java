/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.DeferredCrossProjectDependency;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Pre-resolves {@link LocalTaskNode} dependency relationships in parallel using BFS waves,
 * without mutating the task graph. The result is consumed by the sequential DFS phase in
 * {@link DefaultExecutionPlan}.
 *
 * <p>Node dependencies are resolved wave-by-wave: each wave is grouped by owning project and resolved in
 * parallel under that project's lock. Cross-project dependency lookups that cannot be performed
 * under a single project lock are represented as {@link DeferredCrossProjectNode} placeholders
 * in the dependency sets, resolved after the wave, and then substituted with the real nodes.</p>
 */
@NullMarked
class ParallelNodeRelationshipsResolver {

    private final TaskDependencyResolver dependencyResolver;
    private final WorkerLeaseService workerLeaseService;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectStateRegistry projectStateRegistry;
    private final TaskNodeFactory taskNodeFactory;

    ParallelNodeRelationshipsResolver(
        TaskDependencyResolver dependencyResolver,
        WorkerLeaseService workerLeaseService,
        BuildOperationExecutor buildOperationExecutor,
        ProjectStateRegistry projectStateRegistry,
        TaskNodeFactory taskNodeFactory
    ) {
        this.dependencyResolver = dependencyResolver;
        this.workerLeaseService = workerLeaseService;
        this.buildOperationExecutor = buildOperationExecutor;
        this.projectStateRegistry = projectStateRegistry;
        this.taskNodeFactory = taskNodeFactory;
    }

    /**
     * Pre-resolves relationships for all {@link LocalTaskNode}s reachable from {@code initialQueue}
     * in parallel. Cross-project dependencies are represented as placeholder nodes during resolution,
     * then resolved and substituted with real nodes before returning.
     */
    @SuppressWarnings("NonApiType")
    Map<LocalTaskNode, ResolvedNodeRelationships> resolve(LinkedList<Node> initialQueue) {
        Map<LocalTaskNode, ResolvedNodeRelationships> results = new HashMap<>();
        BfsWaveQueue waves = new BfsWaveQueue(initialQueue);
        CrossProjectPlaceholderRegistry placeholderRegistry = new CrossProjectPlaceholderRegistry();
        Map<ProjectInternal, ProjectScopedTaskDependencyResolver> resolverCache = new HashMap<>();

        while (waves.hasNextWave()) {
            List<LocalTaskNode> currentWave = waves.takeNextWave();

            List<NodeResolutionResult> waveResults = resolveDependenciesInParallel(currentWave, placeholderRegistry, resolverCache);

            for (NodeResolutionResult result : waveResults) {
                results.put(result.node, result.resolved);
                waves.enqueue(result.resolved.getDependencies());
                waves.enqueue(result.resolved.getFinalizedBy());
            }

            List<DeferredCrossProjectNode> placeholders = placeholderRegistry.takePending();
            if (!placeholders.isEmpty()) {
                resolvePlaceholderNodes(placeholders);
                for (DeferredCrossProjectNode deferred : placeholders) {
                    waves.enqueue(deferred.getResolvedNodes());
                }
            }
        }

        return placeholderRegistry.substitutePlaceholders(results);
    }

    /**
     * Resolves dependencies of a wave of {@link LocalTaskNode}s in parallel, grouped by owning project.
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    private List<NodeResolutionResult> resolveDependenciesInParallel(
        List<LocalTaskNode> nodes,
        CrossProjectPlaceholderRegistry placeholders,
        Map<ProjectInternal, ProjectScopedTaskDependencyResolver> resolverCache
    ) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        Map<ProjectInternal, List<LocalTaskNode>> byProject = new LinkedHashMap<>();
        for (LocalTaskNode node : nodes) {
            byProject.computeIfAbsent(node.getOwningProject(), k -> new ArrayList<>()).add(node);
        }

        if (byProject.size() == 1) {
            // Single project: all project locks are already held, so resolve on the calling thread.
            List<NodeResolutionResult> results = new ArrayList<>();
            for (LocalTaskNode node : nodes) {
                results.add(new NodeResolutionResult(node, node.resolveRelationships(dependencyResolver)));
            }
            return results;
        }

        // Multiple projects: resolve each project group in parallel under its own lock.
        List<List<LocalTaskNode>> projectGroups = new ArrayList<>(byProject.values());
        ConcurrentLinkedQueue<NodeResolutionResult> results = new ConcurrentLinkedQueue<>();
        runInParallelWithProjectAccess(queue -> {
            for (List<LocalTaskNode> group : projectGroups) {
                ProjectInternal project = group.get(0).getOwningProject();
                ProjectScopedTaskDependencyResolver resolver = resolverCache.computeIfAbsent(project, p -> createProjectScopedResolver(p, placeholders));
                queue.add(buildOp("Resolve task dependencies for project " + project, () -> {
                    project.getOwner().fromMutableState(p -> {
                        for (LocalTaskNode node : group) {
                            ResolvedNodeRelationships resolved = node.resolveRelationships(resolver);
                            results.add(new NodeResolutionResult(node, resolved));
                        }
                        return null;
                    });
                }));
            }
        });

        return new ArrayList<>(results);
    }

    private ProjectScopedTaskDependencyResolver createProjectScopedResolver(ProjectInternal project, CrossProjectPlaceholderRegistry placeholders) {
        return dependencyResolver.newProjectScopedResolver(project, (dep, task) ->
            placeholders.createCrossProjectPlaceholderNode(dep, task, taskNodeFactory)
        );
    }

    /**
     * Resolves all {@link DeferredCrossProjectNode} placeholders by looking up their target tasks.
     */
    private void resolvePlaceholderNodes(List<DeferredCrossProjectNode> deferredNodes) {
        Map<Path, List<DeferredCrossProjectNode>> byTarget = new LinkedHashMap<>();
        List<DeferredCrossProjectNode> allProjectsSearchNodes = new ArrayList<>();

        for (DeferredCrossProjectNode node : deferredNodes) {
            DeferredCrossProjectDependency dep = node.getDeferredDependency();
            if (dep instanceof DeferredCrossProjectDependency.ByProjectTask) {
                Path targetPath = ((DeferredCrossProjectDependency.ByProjectTask) dep).getTargetProjectIdentityPath();
                byTarget.computeIfAbsent(targetPath, k -> new ArrayList<>()).add(node);
            } else if (dep instanceof DeferredCrossProjectDependency.AllProjectsSearch) {
                allProjectsSearchNodes.add(node);
            }
        }

        // For ByTarget placeholder node find a task
        resolveByTargetPlaceholderNodesToLocalTaskNodes(byTarget);

        // Resolve AllProjectsSearch items sequentially (needs all-projects access)
        for (DeferredCrossProjectNode node : allProjectsSearchNodes) {
            DeferredCrossProjectDependency.AllProjectsSearch search =
                (DeferredCrossProjectDependency.AllProjectsSearch) node.getDeferredDependency();
            node.resolve(resolveAllProjectsSearch(search));
        }
    }

    /**
     * Discovers tasks and resolves {@link DeferredCrossProjectDependency.ByProjectTask} placeholders
     * in parallel, one build operation per target project.
     */
    private void resolveByTargetPlaceholderNodesToLocalTaskNodes(Map<Path, List<DeferredCrossProjectNode>> byTarget) {
        if (byTarget.isEmpty()) {
            return;
        }

        runInParallelWithProjectAccess(queue -> {
            for (Map.Entry<Path, List<DeferredCrossProjectNode>> entry : byTarget.entrySet()) {
                Path projectPath = entry.getKey();
                queue.add(buildOp("Resolve cross-project tasks for " + projectPath, () -> {
                    ProjectState projectState = projectStateRegistry.stateFor(projectPath);
                    projectState.fromMutableState(project -> {
                        projectState.ensureTasksDiscovered();
                        for (DeferredCrossProjectNode node : entry.getValue()) {
                            DeferredCrossProjectDependency.ByProjectTask byProject = (DeferredCrossProjectDependency.ByProjectTask) node.getDeferredDependency();
                            Task task = project.getTasks().findByName(byProject.getTaskName());
                            // Task may not exist — skip silently, consistent with how the sequential resolution path handles missing tasks
                            if (task != null) {
                                node.resolve(Collections.singletonList(taskNodeFactory.getOrCreateNode(task)));
                            }
                        }
                        return null;
                    });
                }));
            }
        });
    }

    private List<Node> resolveAllProjectsSearch(DeferredCrossProjectDependency.AllProjectsSearch search) {
        TaskCollectingContext collectingContext = new TaskCollectingContext(search.getSourceTask());
        search.getResolutionAction().accept(collectingContext);
        List<Node> result = new ArrayList<>();
        for (Task task : collectingContext.collectedTasks) {
            result.add(taskNodeFactory.getOrCreateNode(task));
        }
        return result;
    }


    private void runInParallelWithProjectAccess(Action<BuildOperationQueue<RunnableBuildOperation>> scheduler) {
        workerLeaseService.runAsIsolatedTask(() -> buildOperationExecutor.runAllWithAccessToProjectState(scheduler));
    }

    private static RunnableBuildOperation buildOp(String displayName, Runnable action) {
        return new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                action.run();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(displayName);
            }
        };
    }

    /**
     * Manages BFS wave traversal: deduplication via a seen-set and a pending queue
     * that becomes the next wave on {@link #takeNextWave()}.
     */
    private static class BfsWaveQueue {
        private final Set<LocalTaskNode> seen = new HashSet<>();
        private List<LocalTaskNode> pending = new ArrayList<>();

        BfsWaveQueue(Iterable<? extends Node> seed) {
            enqueue(seed);
        }

        boolean hasNextWave() {
            return !pending.isEmpty();
        }

        List<LocalTaskNode> takeNextWave() {
            List<LocalTaskNode> wave = pending;
            pending = new ArrayList<>();
            return wave;
        }

        void enqueue(Iterable<? extends Node> nodes) {
            for (Node node : nodes) {
                if (node instanceof LocalTaskNode) {
                    LocalTaskNode localNode = (LocalTaskNode) node;
                    if (!localNode.getDependenciesProcessed() && !localNode.isCannotRunInAnyPlan() && seen.add(localNode)) {
                        pending.add(localNode);
                    }
                }
            }
        }
    }

    /**
     * Thread-safe registry that tracks {@link DeferredCrossProjectNode} placeholders
     * created during parallel resolution. Worker threads call {@link #createCrossProjectPlaceholderNode} to
     * create placeholders; the main thread calls {@link #takePending} and
     * {@link #substitutePlaceholders} to drain and replace them.
     */
    private static class CrossProjectPlaceholderRegistry {
        private final ConcurrentLinkedQueue<DeferredCrossProjectNode> pendingQueue = new ConcurrentLinkedQueue<>();
        private final Set<LocalTaskNode> nodesWithPlaceholders = Collections.newSetFromMap(new ConcurrentHashMap<>());

        DeferredCrossProjectNode createCrossProjectPlaceholderNode(DeferredCrossProjectDependency dep, Task task, TaskNodeFactory taskNodeFactory) {
            DeferredCrossProjectNode placeholder = new DeferredCrossProjectNode(dep);
            pendingQueue.add(placeholder);
            if (task != null) {
                nodesWithPlaceholders.add((LocalTaskNode) taskNodeFactory.getOrCreateNode(task));
            }
            return placeholder;
        }

        List<DeferredCrossProjectNode> takePending() {
            List<DeferredCrossProjectNode> result = new ArrayList<>();
            DeferredCrossProjectNode item;
            while ((item = pendingQueue.poll()) != null) {
                result.add(item);
            }
            return result;
        }

        Map<LocalTaskNode, ResolvedNodeRelationships> substitutePlaceholders(Map<LocalTaskNode, ResolvedNodeRelationships> results) {
            for (LocalTaskNode sourceNode : nodesWithPlaceholders) {
                ResolvedNodeRelationships relationships = results.get(sourceNode);
                checkNotNull(relationships, "Node %s was marked as having placeholders but has no resolved relationships", sourceNode);
                results.put(sourceNode, substituteInRelationships(relationships));
            }
            return results;
        }

        private static ResolvedNodeRelationships substituteInRelationships(ResolvedNodeRelationships relationships) {
            Set<Node> newDeps = substitutePlaceholders(relationships.getDependencies());
            Set<Node> newLifecycle = substitutePlaceholders(relationships.getLifecycleDependencies());
            Set<Node> newFinalizedBy = substitutePlaceholders(relationships.getFinalizedBy());
            Set<Node> newMustRunAfter = substitutePlaceholders(relationships.getMustRunAfter());
            Set<Node> newShouldRunAfter = substitutePlaceholders(relationships.getShouldRunAfter());
            if (newDeps == relationships.getDependencies()
                && newLifecycle == relationships.getLifecycleDependencies()
                && newFinalizedBy == relationships.getFinalizedBy()
                && newMustRunAfter == relationships.getMustRunAfter()
                && newShouldRunAfter == relationships.getShouldRunAfter()) {
                return relationships;
            }
            return new ResolvedNodeRelationships(relationships.getNode(), newDeps, newLifecycle, newFinalizedBy, newMustRunAfter, newShouldRunAfter);
        }

        private static Set<Node> substitutePlaceholders(Set<Node> original) {
            if (!hasAnyPlaceholder(original)) {
                return original;
            }
            Set<Node> result = new HashSet<>();
            for (Node n : original) {
                if (n instanceof DeferredCrossProjectNode) {
                    result.addAll(((DeferredCrossProjectNode) n).getResolvedNodes());
                } else {
                    result.add(n);
                }
            }
            return result;
        }

        private static boolean hasAnyPlaceholder(Set<Node> nodes) {
            for (Node n : nodes) {
                if (n instanceof DeferredCrossProjectNode) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Minimal context that collects Task objects added via {@link #add}.
     * Used to re-execute deferred {@link DeferredCrossProjectDependency.AllProjectsSearch} actions.
     */
    private static class TaskCollectingContext extends AbstractTaskDependencyResolveContext {
        private final @Nullable Task sourceTask;
        final List<Task> collectedTasks = new ArrayList<>();

        TaskCollectingContext(@Nullable Task sourceTask) {
            this.sourceTask = sourceTask;
        }

        @Override
        public @Nullable Task getTask() {
            return sourceTask;
        }

        @Override
        public void add(Object dependency) {
            if (dependency instanceof Task) {
                collectedTasks.add((Task) dependency);
            }
        }
    }

    static class NodeResolutionResult {
        final LocalTaskNode node;
        final ResolvedNodeRelationships resolved;

        NodeResolutionResult(LocalTaskNode node, ResolvedNodeRelationships resolved) {
            this.node = node;
            this.resolved = resolved;
        }
    }
}
