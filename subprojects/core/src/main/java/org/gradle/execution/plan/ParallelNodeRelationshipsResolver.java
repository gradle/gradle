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

import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.DeferredCrossProjectDependency;
import org.gradle.api.Action;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Pre-resolves {@link LocalTaskNode} dependency relationships in parallel using BFS waves,
 * without mutating the task graph. The result is consumed by the sequential DFS phase in
 * {@link DefaultExecutionPlan}.
 *
 * <p>Nodes are resolved wave-by-wave: each wave is grouped by owning project and resolved in
 * parallel under that project's lock. Cross-project dependency lookups are deferred via
 * {@link DeferredCrossProjectDependency} markers and resolved at the end under the correct
 * project's lock.</p>
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
     * in parallel. Cross-project dependencies are deferred and resolved at the end.
     */
    @SuppressWarnings("NonApiType")
    Map<LocalTaskNode, ResolvedNodeRelationships> resolve(LinkedList<Node> initialQueue) {
        Map<LocalTaskNode, ResolvedNodeRelationships> results = new HashMap<>();
        Map<ProjectInternal, ParallelTaskDependencyResolver> resolverByProject = new HashMap<>();
        Set<LocalTaskNode> seen = new HashSet<>();
        Set<Path> discoveredProjects = new HashSet<>();

        List<LocalTaskNode> currentWave = new ArrayList<>();
        for (Node node : initialQueue) {
            addToWaveIfNew(node, seen, currentWave);
        }

        while (!currentWave.isEmpty()) {
            // BFS waves: resolve nodes in parallel until no more same-project nodes found
            Map<LocalTaskNode, DeferredNodeRelationships> deferred = runWaveLoop(currentWave, results, resolverByProject, seen);

            if (deferred.isEmpty()) {
                break;
            }

            currentWave = resolveDeferredAndMerge(deferred, results, seen, discoveredProjects);
        }

        return results;
    }

    /**
     * Runs BFS waves: resolves nodes in parallel, discovers new nodes, returns accumulated deferred items.
     */
    private Map<LocalTaskNode, DeferredNodeRelationships> runWaveLoop(
        List<LocalTaskNode> initialWave,
        Map<LocalTaskNode, ResolvedNodeRelationships> results,
        Map<ProjectInternal, ParallelTaskDependencyResolver> resolverByProject,
        Set<LocalTaskNode> seen
    ) {
        Map<LocalTaskNode, DeferredNodeRelationships> deferred = new HashMap<>();
        List<LocalTaskNode> currentWave = initialWave;
        while (!currentWave.isEmpty()) {
            List<NodeResolutionResult> waveResults = resolveWaveInParallel(currentWave, resolverByProject);
            List<LocalTaskNode> nextWave = new ArrayList<>();

            for (NodeResolutionResult result : waveResults) {
                results.put(result.node, result.resolved);
                for (Node dep : result.resolved.getDependencies()) {
                    addToWaveIfNew(dep, seen, nextWave);
                }
                for (Node fin : result.resolved.getFinalizedBy()) {
                    addToWaveIfNew(fin, seen, nextWave);
                }
                if (!result.deferred.isEmpty()) {
                    deferred.put(result.node, result.deferred);
                }
            }

            currentWave = nextWave;
        }
        return deferred;
    }

    /**
     * Resolves a wave of {@link LocalTaskNode}s in parallel, grouped by owning project.
     */
    private List<NodeResolutionResult> resolveWaveInParallel(List<LocalTaskNode> nodes, Map<ProjectInternal, ParallelTaskDependencyResolver> resolverByProject) {
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }

        Map<ProjectInternal, List<LocalTaskNode>> byProject = new LinkedHashMap<>();
        for (LocalTaskNode node : nodes) {
            byProject.computeIfAbsent(node.getOwningProject(), k -> new ArrayList<>()).add(node);
        }

        if (byProject.size() == 1) {
            // Single project: all project locks are already held, so resolve on the calling thread.
            List<NodeResolutionResult> results = new ArrayList<>();
            for (LocalTaskNode node : nodes) {
                results.add(new NodeResolutionResult(node, node.resolveRelationships(dependencyResolver), DeferredNodeRelationships.EMPTY));
            }
            return results;
        }

        // Multiple projects: release the calling thread's all-projects lock and resolve each
        // project group in parallel, each acquiring its own project lock via fromMutableState.
        List<List<LocalTaskNode>> projectGroups = new ArrayList<>(byProject.values());
        ConcurrentLinkedQueue<NodeResolutionResult> results = new ConcurrentLinkedQueue<>();
        runInParallelWithProjectAccess(queue -> {
            for (List<LocalTaskNode> group : projectGroups) {
                ProjectInternal project = group.get(0).getOwningProject();
                ParallelTaskDependencyResolver resolver = resolverByProject.computeIfAbsent(project, dependencyResolver::newParallelResolver);
                queue.add(buildOp("Resolve task dependencies for project " + project, () -> {
                    project.getOwner().fromMutableState(p -> {
                        for (LocalTaskNode node : group) {
                            LocalTaskNode.ResolvedRelationshipsWithDeferred result = node.resolveRelationshipsWithDeferral(resolver);
                            results.add(new NodeResolutionResult(node, result.resolved, result.deferred));
                        }
                        return null;
                    });
                }));
            }
        });

        return new ArrayList<>(results);
    }

    /**
     * Resolves all deferred cross-project items in parallel, merges them into results,
     * and returns any newly discovered nodes for further BFS processing.
     *
     * <p>{@link DeferredCrossProjectDependency.ByProjectTask} items are resolved in parallel
     * (grouped by target project). {@link DeferredCrossProjectDependency.AllProjectsSearch}
     * items are resolved sequentially since they need all-projects access.</p>
     */
    private List<LocalTaskNode> resolveDeferredAndMerge(
        Map<LocalTaskNode, DeferredNodeRelationships> deferred,
        Map<LocalTaskNode, ResolvedNodeRelationships> results,
        Set<LocalTaskNode> seen,
        Set<Path> discoveredProjects
    ) {
        Map<DeferredCrossProjectDependency.ByProjectTask, Node> taskNodes = getOrCreateByProjectTaskNodes(deferred, discoveredProjects);

        List<LocalTaskNode> discoveredNodes = new ArrayList<>();
        for (Map.Entry<LocalTaskNode, DeferredNodeRelationships> entry : deferred.entrySet()) {
            LocalTaskNode sourceNode = entry.getKey();
            DeferredNodeRelationships deferredRels = entry.getValue();

            Set<Node> deps = lookupTaskNodes(deferredRels.dependencies, taskNodes);
            Set<Node> lifecycle = lookupTaskNodes(deferredRels.lifecycleDependencies, taskNodes);
            Set<Node> finalizedBy = lookupTaskNodes(deferredRels.finalizedBy, taskNodes);
            Set<Node> mustRunAfter = lookupTaskNodes(deferredRels.mustRunAfter, taskNodes);
            Set<Node> shouldRunAfter = lookupTaskNodes(deferredRels.shouldRunAfter, taskNodes);

            // Merge into existing results
            ResolvedNodeRelationships existing = checkNotNull(results.get(sourceNode));
            results.put(sourceNode, existing.withAdditionalRelationships(
                deps, lifecycle, finalizedBy, mustRunAfter, shouldRunAfter
            ));

            // Discover new nodes from dependency-creating relationships only
            for (Node node : deps) {
                addToWaveIfNew(node, seen, discoveredNodes);
            }
            for (Node node : lifecycle) {
                addToWaveIfNew(node, seen, discoveredNodes);
            }
            for (Node node : finalizedBy) {
                addToWaveIfNew(node, seen, discoveredNodes);
            }
        }

        return discoveredNodes;
    }

    /**
     * Resolves all {@link DeferredCrossProjectDependency.ByProjectTask} items across all deferred
     * relationships. Task discovery is performed in parallel per project; task lookup and node
     * creation are cheap and done sequentially.
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    private Map<DeferredCrossProjectDependency.ByProjectTask, Node> getOrCreateByProjectTaskNodes(
        Map<LocalTaskNode, DeferredNodeRelationships> allDeferred,
        Set<Path> discoveredProjects
    ) {
        // Collect all ByProjectTask items, grouped by target project
        Map<Path, List<DeferredCrossProjectDependency.ByProjectTask>> byTarget = new LinkedHashMap<>();
        for (DeferredNodeRelationships deferred : allDeferred.values()) {
            collectByProjectTasks(deferred.dependencies, byTarget);
            collectByProjectTasks(deferred.lifecycleDependencies, byTarget);
            collectByProjectTasks(deferred.finalizedBy, byTarget);
            collectByProjectTasks(deferred.mustRunAfter, byTarget);
            collectByProjectTasks(deferred.shouldRunAfter, byTarget);
        }

        if (byTarget.isEmpty()) {
            return Collections.emptyMap();
        }

        // Discover tasks in parallel for each project group, since it's kinda expensive
        discoverTasksInParallel(byTarget.keySet(), discoveredProjects);

        // Task lookup and node creation is cheap — do it sequentially
        Map<DeferredCrossProjectDependency.ByProjectTask, Node> resolved = new HashMap<>();
        for (Map.Entry<Path, List<DeferredCrossProjectDependency.ByProjectTask>> projectGroup : byTarget.entrySet()) {
            ProjectInternal project = projectStateRegistry.stateFor(projectGroup.getKey()).getMutableModel();
            for (DeferredCrossProjectDependency.ByProjectTask item : projectGroup.getValue()) {
                Task task = project.getTasks().findByName(item.getTaskName());
                if (task != null) {
                    resolved.put(item, taskNodeFactory.getOrCreateNode(task));
                }
            }
        }
        return resolved;
    }

    /**
     * Looks up resolved nodes for a list of deferred items from the pre-resolved cache.
     */
    private Set<Node> lookupTaskNodes(
        List<DeferredCrossProjectDependency> items,
        Map<DeferredCrossProjectDependency.ByProjectTask, Node> taskNodes
    ) {
        if (items.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Node> result = new HashSet<>();
        for (DeferredCrossProjectDependency item : items) {
            if (item instanceof DeferredCrossProjectDependency.ByProjectTask) {
                Node node = taskNodes.get(item);
                if (node != null) {
                    result.add(node);
                }
            } else if (item instanceof DeferredCrossProjectDependency.AllProjectsSearch) {
                result.addAll(resolveAllProjectsSearch((DeferredCrossProjectDependency.AllProjectsSearch) item));
            }
        }
        return result;
    }

    /**
     * Ensures tasks are discovered for the given projects in parallel, skipping projects
     * that have already been discovered in a previous iteration.
     */
    private void discoverTasksInParallel(Set<Path> projectPaths, Set<Path> alreadyDiscovered) {
        List<Path> toDiscover = new ArrayList<>();
        for (Path projectPath : projectPaths) {
            if (alreadyDiscovered.add(projectPath)) {
                toDiscover.add(projectPath);
            }
        }
        if (!toDiscover.isEmpty()) {
            runInParallelWithProjectAccess(queue -> {
                for (Path projectPath : toDiscover) {
                    queue.add(buildOp("Discover tasks for project " + projectPath, () -> {
                        ProjectState projectState = projectStateRegistry.stateFor(projectPath);
                        projectState.fromMutableState(p -> {
                            projectState.ensureTasksDiscovered();
                            return null;
                        });
                    }));
                }
            });
        }
    }

    private static void collectByProjectTasks(
        List<DeferredCrossProjectDependency> items,
        Map<Path, List<DeferredCrossProjectDependency.ByProjectTask>> byTarget
    ) {
        for (DeferredCrossProjectDependency item : items) {
            if (item instanceof DeferredCrossProjectDependency.ByProjectTask) {
                DeferredCrossProjectDependency.ByProjectTask byProject = (DeferredCrossProjectDependency.ByProjectTask) item;
                byTarget.computeIfAbsent(byProject.getTargetProjectIdentityPath(), k -> new ArrayList<>()).add(byProject);
            }
        }
    }

    private List<Node> resolveAllProjectsSearch(DeferredCrossProjectDependency.AllProjectsSearch search) {
        TaskCollectingContext collectingContext = new TaskCollectingContext();
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

    private static void addToWaveIfNew(Node node, Set<LocalTaskNode> seen, List<LocalTaskNode> wave) {
        if (node instanceof LocalTaskNode) {
            LocalTaskNode localNode = (LocalTaskNode) node;
            if (!localNode.getDependenciesProcessed() && !localNode.isCannotRunInAnyPlan() && seen.add(localNode)) {
                wave.add(localNode);
            }
        }
    }

    /**
     * Minimal context that collects Task objects added via {@link #add}.
     * Used to re-execute deferred {@link DeferredCrossProjectDependency.AllProjectsSearch} actions.
     */
    private static class TaskCollectingContext extends AbstractTaskDependencyResolveContext {
        final List<Task> collectedTasks = new ArrayList<>();

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
        final DeferredNodeRelationships deferred;

        NodeResolutionResult(LocalTaskNode node, ResolvedNodeRelationships resolved, DeferredNodeRelationships deferred) {
            this.node = node;
            this.resolved = resolved;
            this.deferred = deferred;
        }
    }
}
