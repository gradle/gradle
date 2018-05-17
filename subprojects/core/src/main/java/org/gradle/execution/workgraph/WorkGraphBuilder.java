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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.scheduler.CycleReporter;
import org.gradle.internal.scheduler.Edge;
import org.gradle.internal.scheduler.EdgeType;
import org.gradle.internal.scheduler.Graph;
import org.gradle.internal.scheduler.Node;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.scheduler.EdgeType.AVOID_STARTING_BEFORE_FINALIZED;
import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF;
import static org.gradle.internal.scheduler.EdgeType.FINALIZED_BY;
import static org.gradle.internal.scheduler.EdgeType.MUST_COMPLETE_BEFORE;
import static org.gradle.internal.scheduler.EdgeType.SHOULD_COMPLETE_BEFORE;
import static org.gradle.internal.scheduler.NodeState.SHOULD_RUN;

public class WorkGraphBuilder {
    private static final Graph.LiveEdgeDetector LIVE_EDGE_DETECTOR = new Graph.LiveEdgeDetector() {
        @Override
        public boolean isIncomingEdgeLive(Edge edge) {
            return edge.getType() == DEPENDENCY_OF;
        }

        @Override
        public boolean isOutgoingEdgeLive(Edge edge) {
            return edge.getType() == FINALIZED_BY;
        }
    };

    private final Set<Task> requestedTasks = Sets.newLinkedHashSet();
    private Spec<? super Task> filter = Specs.satisfyAll();

    public void setFilter(@Nullable Spec<? super Task> filter) {
        if (filter == null) {
            this.filter = Specs.<Task>satisfyAll();
        } else {
            this.filter = filter;
        }
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        Iterables.addAll(requestedTasks, tasks);
    }

    public WorkGraph build(CycleReporter cycleReporter) {
        Graph graph = new Graph();
        Map<Task, TaskNode> taskNodes = Maps.newLinkedHashMap();
        List<Task> sortedTasks = Lists.newArrayList(requestedTasks);
        Collections.sort(sortedTasks);
        ImmutableSet.Builder<Task> filteredTasks = ImmutableSet.builder();

        List<TaskNode> requestedNodes = Lists.newArrayListWithCapacity(sortedTasks.size());
        for (Task requestedTask : sortedTasks) {
            TaskNode requestedNode = createTaskNodeUnlessFiltered(graph, taskNodes, requestedTask);
            if (requestedNode != null) {
                requestedNodes.add(requestedNode);
            } else {
                filteredTasks.add(requestedTask);
            }
        }

        Deque<TaskNode> queue = new ArrayDeque<TaskNode>(requestedNodes);
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();
        Set<TaskNode> visitedNodes = Sets.newHashSet();
        while (true) {
            TaskNode node = queue.poll();
            if (node == null) {
                break;
            }

            if (!visitedNodes.add(node)) {
                continue;
            }

            TaskInternal task = node.getTask();
            ((ProjectInternal) task.getProject()).getTasks().prepareForExecution(task);

            for (Task dependencyTask : context.getDependencies(task, task.getTaskDependencies())) {
                TaskNode dependencyNode = addEdge(graph, taskNodes, dependencyTask, DEPENDENCY_OF, node);
                if (dependencyNode != null) {
                    queue.add(dependencyNode);
                } else {
                    filteredTasks.add(dependencyTask);
                }
            }
            for (Task finalizerTask : context.getDependencies(task, task.getFinalizedBy())) {
                TaskNode finalizerNode = addEdge(graph, taskNodes, node, FINALIZED_BY, finalizerTask);
                if (finalizerNode != null) {
                    queue.add(finalizerNode);
                } else {
                    filteredTasks.add(finalizerTask);
                }
            }
            for (Task mustRunAfterTask : context.getDependencies(task, task.getMustRunAfter())) {
                addEdge(graph, taskNodes, mustRunAfterTask, MUST_COMPLETE_BEFORE, node);
            }
            for (Task shouldRunAfterTask : context.getDependencies(task, task.getShouldRunAfter())) {
                addEdge(graph, taskNodes, shouldRunAfterTask, SHOULD_COMPLETE_BEFORE, node);
            }
        }

        graph.removeDeadNodes(requestedNodes, LIVE_EDGE_DETECTOR);
        connectFinalizerDependencies(graph);
        Graph dag = graph.breakCycles(cycleReporter);
        markEntryNodesAsShouldRun(dag, requestedNodes);
        return new WorkGraph(dag, ImmutableMap.copyOf(taskNodes), ImmutableSet.copyOf(requestedNodes), filteredTasks.build());
    }

    @Nullable
    private TaskNode addEdge(Graph graph, Map<Task, TaskNode> taskNodes, TaskNode sourceNode, EdgeType type, Task targetTask) {
        TaskNode targetNode = getOrCreateTaskNode(graph, taskNodes, targetTask);
        if (targetNode == null) {
            return null;
        }
        graph.addEdge(new Edge(sourceNode, type, targetNode));
        return targetNode;
    }

    @Nullable
    private TaskNode addEdge(Graph graph, Map<Task, TaskNode> taskNodes, Task sourceTask, EdgeType type, TaskNode targetNode) {
        TaskNode sourceNode = getOrCreateTaskNode(graph, taskNodes, sourceTask);
        if (sourceNode == null) {
            return null;
        }
        graph.addEdge(new Edge(sourceNode, type, targetNode));
        return sourceNode;
    }

    @Nullable
    private TaskNode getOrCreateTaskNode(Graph graph, Map<Task, TaskNode> taskNodes, Task task) {
        TaskNode node = taskNodes.get(task);
        if (node == null) {
            node = createTaskNodeUnlessFiltered(graph, taskNodes, task);
        }
        return node;
    }

    @Nullable
    private TaskNode createTaskNodeUnlessFiltered(Graph graph, Map<Task, TaskNode> taskNodes, Task task) {
        if (!filter.isSatisfiedBy(task)) {
            return null;
        }
        TaskNode node = new TaskNode((TaskInternal) task);
        graph.addNode(node);
        taskNodes.put(task, node);
        return node;
    }

    private static void connectFinalizerDependencies(final Graph graph) {
        for (Edge edge : graph.getAllEdges()) {
            if (edge.getType() != FINALIZED_BY) {
                continue;
            }
            final Set<Edge> edgesAddedFromFinalized = Sets.newHashSet();
            final Node finalized = edge.getSource();
            Node finalizer = edge.getTarget();
            graph.walkIncomingEdgesFrom(finalizer, new Graph.EdgeWalkerAction() {
                @Override
                public boolean execute(Edge edge) {
                    if (edge.getType() != DEPENDENCY_OF) {
                        return false;
                    }
                    Node finalizerDependency = edge.getSource();
                    if (finalizerDependency == finalized) {
                        return false;
                    }
                    Edge finalizerDependencyConstraint = new Edge(finalized, AVOID_STARTING_BEFORE_FINALIZED, finalizerDependency);
                    if (edgesAddedFromFinalized.add(finalizerDependencyConstraint)) {
                        graph.addEdge(finalizerDependencyConstraint);
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private static void markEntryNodesAsShouldRun(Graph graph, Iterable<? extends Node> entryNodes) {
        for (Node entryNode : entryNodes) {
            entryNode.setState(SHOULD_RUN);
            graph.walkIncomingEdgesFrom(entryNode, new Graph.EdgeWalkerAction() {
                @Override
                public boolean execute(Edge edge) {
                    if (edge.getType() != DEPENDENCY_OF) {
                        return false;
                    }
                    edge.getSource().setState(SHOULD_RUN);
                    return true;
                }
            });
        }
    }


}
