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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLeaseService;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
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
 * parallel under that project's lock.</p>
 */
@NullMarked
class ParallelNodeRelationshipsResolver {

    private final TaskDependencyResolver dependencyResolver;
    private final WorkerLeaseService workerLeaseService;
    private final BuildOperationExecutor buildOperationExecutor;

    ParallelNodeRelationshipsResolver(
        TaskDependencyResolver dependencyResolver,
        WorkerLeaseService workerLeaseService,
        BuildOperationExecutor buildOperationExecutor
    ) {
        this.dependencyResolver = dependencyResolver;
        this.workerLeaseService = workerLeaseService;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    /**
     * Pre-resolves relationships for all {@link LocalTaskNode}s reachable from {@code initialQueue}
     * in parallel, returning a map of node → resolved relationships.
     */
    @SuppressWarnings("NonApiType")
    Map<LocalTaskNode, ResolvedNodeRelationships> resolve(LinkedList<Node> initialQueue) {
        Map<LocalTaskNode, ResolvedNodeRelationships> preResolved = new HashMap<>();
        Set<LocalTaskNode> seen = new HashSet<>();

        List<LocalTaskNode> currentWave = new ArrayList<>();
        for (Node node : initialQueue) {
            addToWaveIfNew(node, seen, currentWave);
        }

        while (!currentWave.isEmpty()) {
            List<ResolvedNodeRelationships> waveResults = resolveWaveInParallel(currentWave);
            List<LocalTaskNode> nextWave = new ArrayList<>();

            for (ResolvedNodeRelationships resolved : waveResults) {
                preResolved.put(resolved.getNode(), resolved);
                for (Node dependency : resolved.getDependencies()) {
                    addToWaveIfNew(dependency, seen, nextWave);
                }
                for (Node finalizer : resolved.getFinalizedBy()) {
                    addToWaveIfNew(finalizer, seen, nextWave);
                }
            }

            currentWave = nextWave;
        }

        return preResolved;
    }

    /**
     * Resolves a wave of {@link LocalTaskNode}s in parallel, grouped by owning project.
     * Each project group gets its own {@link TaskDependencyResolver} to avoid thread-safety issues
     * on the shared {@link org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext}.
     */
    private List<ResolvedNodeRelationships> resolveWaveInParallel(List<LocalTaskNode> nodes) {
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }

        Map<ProjectInternal, List<LocalTaskNode>> byProject = new LinkedHashMap<>();
        for (LocalTaskNode node : nodes) {
            byProject.computeIfAbsent(node.getOwningProject(), k -> new ArrayList<>()).add(node);
        }

        if (byProject.size() == 1) {
            // Single project: all project locks are already held, so resolve on the calling thread.
            List<ResolvedNodeRelationships> results = new ArrayList<>();
            for (LocalTaskNode node : nodes) {
                results.add(node.resolveRelationships(dependencyResolver));
            }
            return results;
        }

        // Multiple projects: release the calling thread's all-projects lock and resolve each
        // project group in parallel, each acquiring its own project lock via fromMutableState.
        List<List<LocalTaskNode>> projectGroups = new ArrayList<>(byProject.values());
        ConcurrentLinkedQueue<ResolvedNodeRelationships> results = new ConcurrentLinkedQueue<>();
        workerLeaseService.runAsIsolatedTask(() -> {
            buildOperationExecutor.runAllWithAccessToProjectState(queue -> {
                for (List<LocalTaskNode> group : projectGroups) {
                    TaskDependencyResolver resolver = dependencyResolver.newResolver();
                    queue.add(new RunnableBuildOperation() {
                        @Override
                        public void run(BuildOperationContext context) {
                            ProjectState projectState = group.get(0).getOwningProject().getOwner();
                            projectState.fromMutableState(project -> {
                                for (LocalTaskNode node : group) {
                                    results.add(node.resolveRelationships(resolver));
                                }
                                return null;
                            });
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName("Resolve task dependencies for project " + group.get(0).getOwningProject());
                        }
                    });
                }
            });
        });

        return new ArrayList<>(results);
    }

    private static void addToWaveIfNew(Node node, Set<LocalTaskNode> seen, List<LocalTaskNode> wave) {
        if (node instanceof LocalTaskNode) {
            LocalTaskNode localNode = (LocalTaskNode) node;
            if (!localNode.getDependenciesProcessed() && !localNode.isCannotRunInAnyPlan() && seen.add(localNode)) {
                wave.add(localNode);
            }
        }
    }
}
