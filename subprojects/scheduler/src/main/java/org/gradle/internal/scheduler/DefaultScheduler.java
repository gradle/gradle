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

package org.gradle.internal.scheduler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import org.gradle.api.BuildCancelledException;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.resources.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static org.gradle.internal.scheduler.EdgeType.MUST_NOT_RUN_WITH;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.KEEP;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.REMOVE;
import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.MUST_RUN;

public class DefaultScheduler implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);
    private static final int MAX_WORKERS = 16;
    private static final int EVENTS_TO_PROCESS_AT_ONCE = 2 * MAX_WORKERS;

    private final BlockingQueue<Event> eventQueue = Queues.newArrayBlockingQueue(MAX_WORKERS);
    private final Set<Node> runningNodes = Sets.newLinkedHashSet();
    private final List<Event> eventsBeingProcessed = Lists.newArrayListWithCapacity(EVENTS_TO_PROCESS_AT_ONCE);
    private final BuildCancellationToken cancellationToken;
    private final ConcurrentNodeExecutionCoordinator concurrentNodeExecutionCoordinator;
    private final NodeExecutionWorkerService workerService;
    private boolean cancelled;

    public DefaultScheduler(
            BuildCancellationToken cancellationToken,
            ConcurrentNodeExecutionCoordinator concurrentNodeExecutionCoordinator,
            NodeExecutionWorkerService workerService
    ) {
        this.cancellationToken = cancellationToken;
        this.concurrentNodeExecutionCoordinator = concurrentNodeExecutionCoordinator;
        this.workerService = workerService;
    }

    @Override
    public GraphExecutionResult execute(Graph graph, Collection<? extends Node> entryNodes, boolean continueOnFailure, NodeExecutor nodeExecutor) {
        workerService.start(nodeExecutor, eventQueue);
        try {
            ImmutableList.Builder<Node> executedNodes = ImmutableList.builder();
            ImmutableList.Builder<Throwable> failures = ImmutableList.builder();

            execute(graph, continueOnFailure, executedNodes, failures);
            if (cancelled) {
                failures.add(new BuildCancelledException());
            }
            return new GraphExecutionResult(executedNodes.build(), failures.build());
        } finally {
            workerService.close();
        }
    }

    private void cancelExecution(Graph graph, boolean cancelMustRun) {
        for (Node node : graph.getAllNodes()) {
            // Allow currently executing and enforced tasks to complete, but skip everything else.
            switch (node.getState()) {
                case RUNNABLE:
                case SHOULD_RUN:
                    node.setState(CANCELLED);
                    cancelled = true;
                    break;
                case MUST_RUN:
                    if (cancelMustRun) {
                        node.setState(CANCELLED);
                        cancelled = true;
                    }
                    break;
                case CANCELLED:
                case DEPENDENCY_FAILED:
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    private void execute(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
        while (graph.hasNodes()) {
            scheduleWork(graph);
            if (cancelled) {
                break;
            }
            handleEvents(graph, continueOnFailure, executedNodes, failures);
        }
    }

    private void scheduleWork(Graph graph) {
        Queue<Node> rootNodes = graph.queueRootNodes();
        System.out.printf(">> Scheduling root nodes: %s%n", rootNodes);

        if (cancellationToken.isCancellationRequested()) {
            cancelExecution(graph, false);
            return;
        }

        if (rootNodes.isEmpty()) {
            return;
        }

        while (true) {
            // Find a root node to allocate
            Node nodeToRun = rootNodes.poll();
            if (nodeToRun == null) {
                break;
            }
            if (runningNodes.contains(nodeToRun)) {
                System.out.printf(">> Node %s is already running, skipping%n", nodeToRun);
                continue;
            }

            if (!nodeToRun.getState().isExecutable()) {
                eventQueue.add(new NodeFinishedEvent(nodeToRun));
                continue;
            }

            // Check if there is a node conflicting with the current one
            Node conflictingNode = concurrentNodeExecutionCoordinator.findConflictingNode(graph, nodeToRun, runningNodes);
            if (conflictingNode != null) {
                // Make sure we don't retry this node until the conflicting node is finished
                System.out.printf(">> Found conflict between %s and %s%n", nodeToRun, conflictingNode);
                graph.addEdge(new Edge(conflictingNode, MUST_NOT_RUN_WITH, nodeToRun));
                continue;
            }

            // Allocate a worker
            NodeExecutionWorker worker = workerService.getNextAvailableWorker();
            if (worker == null) {
                System.out.printf(">> No available worker found, stopping execution%n");
                return;
            }

            // Run the node
            System.out.printf(">> Trying to run %s...%n", nodeToRun);
            // TODO Don't do this for revived nodes that have already been prepared once?
            prepareToRunNode(nodeToRun, graph, rootNodes);
            ResourceLock nodeLock = concurrentNodeExecutionCoordinator.findLockFor(nodeToRun);
            NodeExecutionWorker.NodeSchedulingResult schedulingResult = worker.schedule(nodeToRun, nodeLock);
            switch (schedulingResult) {
                case NO_WORKER_LEASE:
                    System.out.printf(">> Worker lease not available for %s, stopping scheduling round%n", nodeToRun);
                    return;
                case NO_RESOURCE_LOCK:
                    System.out.printf(">> Resource lock not available for %s, scheduling next task%n", nodeToRun);
                    break;
                case STARTED:
                    System.out.printf(">> Node %s started, scheduling next task%n", nodeToRun);
                    runningNodes.add(nodeToRun);
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    private void handleEvents(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
        try {
            // Wait for at least one event to arrive
            Event event = eventQueue.take();
            eventsBeingProcessed.add(event);
            // Make sure we take all available events
            eventQueue.drainTo(eventsBeingProcessed, EVENTS_TO_PROCESS_AT_ONCE - 1);
        } catch (InterruptedException e) {
            LOGGER.info("Build has been interrupted", e);
            failures.add(e);
            cancelExecution(graph, true);
        }

        for (Event event : eventsBeingProcessed) {
            System.out.printf(">> Handling event %s%n", event);
            runningNodes.remove(event.node);
            event.handle(graph, continueOnFailure, executedNodes, failures);
        }
        eventsBeingProcessed.clear();
        System.out.printf(">> Nodes after handling events: %s%n", graph.getAllNodes());
    }

    private static void prepareToRunNode(Node nodeToRun, Graph graph, Queue<Node> rootNodes) {
        graph.processOutgoingEdges(nodeToRun, rootNodes, new Graph.EdgeAction() {
            @Override
            public Graph.EdgeActionResult process(Edge edge) {
                Node target = edge.getTarget();
                EdgeType type = edge.getType();
                // Everything that's involved in finalizing this node must now run
                switch (type) {
                    case FINALIZED_BY:
                    case AVOID_STARTING_BEFORE_FINALIZED:
                        switch (target.getState()) {
                            case RUNNABLE:
                            case SHOULD_RUN:
                            case CANCELLED:
                                target.setState(MUST_RUN);
                                break;
                            default:
                                // Otherwise it's either failed or already set to MUST_RUN
                                break;
                        }
                        break;
                    default:
                        break;
                }
                // Since the node is now starting, we can unlock nodes that shouldn't have been started before
                switch (type) {
                    case DEPENDENCY_OF:
                    case MUST_NOT_RUN_WITH:
                    case MUST_COMPLETE_BEFORE:
                    case SHOULD_COMPLETE_BEFORE:
                    case FINALIZED_BY:
                        return KEEP;
                    case AVOID_STARTING_BEFORE_FINALIZED:
                        return REMOVE;
                    default:
                        throw new AssertionError();
                }
            }
        });
    }
}
