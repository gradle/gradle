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

/**
 * Pre-resolves {@link LocalTaskNode} dependency relationships in parallel using BFS waves,
 * without mutating the task graph. The result is consumed by the sequential DFS phase in
 * {@link DefaultExecutionPlan}.
 *
 * <p>Nodes are resolved wave-by-wave: each wave is grouped by owning project and resolved in
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
        Set<LocalTaskNode> seen = new HashSet<>();
        Set<Path> discoveredProjects = new HashSet<>();

        // Shared across all waves — the factory in cached resolvers writes to these queues
        ConcurrentLinkedQueue<DeferredCrossProjectNode> deferredNodesQueue = new ConcurrentLinkedQueue<>();
        Set<LocalTaskNode> nodesWithPlaceholders = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        Map<ProjectInternal, ParallelTaskDependencyResolver> resolverByProject = new HashMap<>();

        List<LocalTaskNode> currentWave = new ArrayList<>();
        for (Node node : initialQueue) {
            addToWaveIfNew(node, seen, currentWave);
        }

        while (!currentWave.isEmpty()) {
            List<NodeResolutionResult> waveResults = resolveWaveInParallel(currentWave, resolverByProject, deferredNodesQueue, nodesWithPlaceholders);

            List<LocalTaskNode> nextWave = new ArrayList<>();
            for (NodeResolutionResult result : waveResults) {
                results.put(result.node, result.resolved);
                for (Node dep : result.resolved.getDependencies()) {
                    addToWaveIfNew(dep, seen, nextWave);
                }
                for (Node fin : result.resolved.getFinalizedBy()) {
                    addToWaveIfNew(fin, seen, nextWave);
                }
            }

            // Drain deferred nodes produced during this wave
            List<DeferredCrossProjectNode> waveDeferredNodes = drainQueue(deferredNodesQueue);
            if (!waveDeferredNodes.isEmpty()) {
                resolveDeferredNodes(waveDeferredNodes, discoveredProjects);
                for (DeferredCrossProjectNode deferred : waveDeferredNodes) {
                    deferred.getResolvedNodes().forEach(node -> addToWaveIfNew(node, seen, nextWave));
                }
            }

            currentWave = nextWave;
        }

        if (!nodesWithPlaceholders.isEmpty()) {
            substitutePlaceholders(results, nodesWithPlaceholders);
        }

        return results;
    }

    /**
     * Resolves a wave of {@link LocalTaskNode}s in parallel, grouped by owning project.
     * Cross-project dependencies are converted to {@link DeferredCrossProjectNode} placeholders
     * by the resolver's context factory, which writes them to the shared {@code deferredNodesQueue}.
     */
    private List<NodeResolutionResult> resolveWaveInParallel(
        List<LocalTaskNode> nodes,
        Map<ProjectInternal, ParallelTaskDependencyResolver> resolverByProject,
        ConcurrentLinkedQueue<DeferredCrossProjectNode> deferredNodesQueue,
        Set<LocalTaskNode> nodesWithPlaceholders
    ) {
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }

        Map<ProjectInternal, List<LocalTaskNode>> byProject = new LinkedHashMap<>();
        for (LocalTaskNode node : nodes) {
            byProject.computeIfAbsent(node.getOwningProject(), k -> new ArrayList<>()).add(node);
        }

        if (byProject.size() == 1) {
            // Single project: all project locks are already held, so resolve on the calling thread.
            // No cross-project deferral needed since everything is same-project.
            List<NodeResolutionResult> results = new ArrayList<>();
            for (LocalTaskNode node : nodes) {
                results.add(new NodeResolutionResult(node, node.resolveRelationships(dependencyResolver)));
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
                ParallelTaskDependencyResolver resolver = resolverByProject.computeIfAbsent(project,
                    p -> createParallelResolver(p, deferredNodesQueue, nodesWithPlaceholders)
                );
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

    /**
     * Resolves all {@link DeferredCrossProjectNode} placeholders by looking up their target
     * tasks. {@link DeferredCrossProjectDependency.ByProjectTask} items have their target
     * projects' tasks discovered in parallel; {@link DeferredCrossProjectDependency.AllProjectsSearch}
     * items are resolved sequentially since they need all-projects access.
     */
    private void resolveDeferredNodes(List<DeferredCrossProjectNode> deferredNodes, Set<Path> discoveredProjects) {
        // Group ByProjectTask items by target project
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

        // Discover tasks in parallel for target projects
        discoverTasksInParallel(byTarget.keySet(), discoveredProjects);

        // Resolve ByProjectTask items — task lookup and node creation is cheap
        for (Map.Entry<Path, List<DeferredCrossProjectNode>> entry : byTarget.entrySet()) {
            ProjectInternal project = projectStateRegistry.stateFor(entry.getKey()).getMutableModel();
            for (DeferredCrossProjectNode node : entry.getValue()) {
                DeferredCrossProjectDependency.ByProjectTask byProject =
                    (DeferredCrossProjectDependency.ByProjectTask) node.getDeferredDependency();
                Task task = project.getTasks().findByName(byProject.getTaskName());
                if (task != null) {
                    node.resolve(Collections.singletonList(taskNodeFactory.getOrCreateNode(task)));
                }
            }
        }

        // Resolve AllProjectsSearch items sequentially
        for (DeferredCrossProjectNode node : allProjectsSearchNodes) {
            DeferredCrossProjectDependency.AllProjectsSearch search =
                (DeferredCrossProjectDependency.AllProjectsSearch) node.getDeferredDependency();
            node.resolve(resolveAllProjectsSearch(search));
        }
    }

    /**
     * Substitutes {@link DeferredCrossProjectNode} placeholders with their resolved real nodes,
     * only for the nodes known to contain placeholders.
     */
    private static void substitutePlaceholders(
        Map<LocalTaskNode, ResolvedNodeRelationships> results,
        Set<LocalTaskNode> nodesWithPlaceholders
    ) {
        for (LocalTaskNode sourceNode : nodesWithPlaceholders) {
            ResolvedNodeRelationships rels = results.get(sourceNode);
            if (rels != null) {
                results.put(sourceNode, rels.substitutePlaceholders());
            }
        }
    }

    private static <T> List<T> drainQueue(ConcurrentLinkedQueue<T> queue) {
        List<T> result = new ArrayList<>();
        T item;
        while ((item = queue.poll()) != null) {
            result.add(item);
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

    private List<Node> resolveAllProjectsSearch(DeferredCrossProjectDependency.AllProjectsSearch search) {
        TaskCollectingContext collectingContext = new TaskCollectingContext();
        search.getResolutionAction().accept(collectingContext);
        List<Node> result = new ArrayList<>();
        for (Task task : collectingContext.collectedTasks) {
            result.add(taskNodeFactory.getOrCreateNode(task));
        }
        return result;
    }

    private ParallelTaskDependencyResolver createParallelResolver(
        ProjectInternal project,
        ConcurrentLinkedQueue<DeferredCrossProjectNode> deferredNodesQueue,
        Set<LocalTaskNode> nodesWithPlaceholders
    ) {
        return dependencyResolver.newParallelResolver(project, (dep, task) -> {
            DeferredCrossProjectNode placeholder = new DeferredCrossProjectNode(dep);
            deferredNodesQueue.add(placeholder);
            if (task != null) {
                nodesWithPlaceholders.add((LocalTaskNode) taskNodeFactory.getOrCreateNode(task));
            }
            return placeholder;
        });
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

        NodeResolutionResult(LocalTaskNode node, ResolvedNodeRelationships resolved) {
            this.node = node;
            this.resolved = resolved;
        }
    }
}
