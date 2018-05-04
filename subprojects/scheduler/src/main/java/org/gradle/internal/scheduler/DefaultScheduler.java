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

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static org.gradle.internal.scheduler.Graph.EdgeActionResult.KEEP;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.REMOVE;
import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.MUST_RUN;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;

public class DefaultScheduler implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);
    private static final int MAX_WORKERS = 16;
    private static final int EVENTS_TO_PROCESS_AT_ONCE = 2 * MAX_WORKERS;

    private final BlockingQueue<Event> eventQueue = Queues.newArrayBlockingQueue(MAX_WORKERS);
    private final Set<Node> runningNodes = Sets.newLinkedHashSet();
    private final Graph graph;
    private final boolean continueOnFailure;
    private final WorkerPool workerPool;

    public DefaultScheduler(Graph graph, boolean continueOnFailure, WorkerPool workerPool) {
        this.graph = graph;
        this.continueOnFailure = continueOnFailure;
        this.workerPool = workerPool;
    }

    public void executeGraph() {
        List<Event> eventsToProcess = Lists.newArrayListWithCapacity(EVENTS_TO_PROCESS_AT_ONCE);
        while (graph.hasNodes()) {
            // Schedule some work if we can
            scheduleWork(graph.getRootNodes());

            // Handle events
            try {
                // Wait for at least one event to arrive
                Event event = eventQueue.take();
                eventsToProcess.add(event);
                // Make sure we take all available events
                eventQueue.drainTo(eventsToProcess, EVENTS_TO_PROCESS_AT_ONCE - 1);
            } catch (InterruptedException e) {
                // TODO Handle execution aborted
                throw new RuntimeException("Handle execution aborted");
            }
            for (Event event : eventsToProcess) {
                LOGGER.debug("> Handling event {}", event);
                event.handle(graph, runningNodes);
            }
            eventsToProcess.clear();
        }
    }

    private void scheduleWork(Iterable<Node> rootNodes) {
        boolean expectAvailableWorkers = true;
        for (Node nodeToRun : rootNodes) {
            LOGGER.debug("> Trying to run {}", nodeToRun);
            switch (nodeToRun.getState()) {
                case RUNNABLE:
                case MUST_RUN:
                    if (expectAvailableWorkers) {
                        if (!tryRunNode(nodeToRun)) {
                            expectAvailableWorkers = false;
                        }
                    }
                    break;
                case CANCELLED:
                case FAILED:
                    handleNodeSkipped(nodeToRun);
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    /**
     * Tries to run the given node.
     * @return whether the operation was successful.
     */
    private boolean tryRunNode(final Node nodeToRun) {
        for (Node runningNode : runningNodes) {
            if (!runningNode.canExecuteInParallelWith(nodeToRun)) {
                graph.addEdge(new Edge(runningNode, nodeToRun, EdgeType.MUST_NOT_RUN_WITH));
                return true;
            }
        }

        boolean workSubmittedSuccessfully = workerPool.tryRunWithAnAllocatedWorker(new Runnable() {
            @Override
            public void run() {
                nodeToRun.execute();
                eventQueue.add(new NodeFinishedEvent(nodeToRun));
            }
        });

        if (!workSubmittedSuccessfully) {
            return false;
        }

        graph.processOutgoingEdges(nodeToRun, new Graph.EdgeAction() {
            @Override
            public Graph.EdgeActionResult process(Edge edge) {
                Node target = edge.getTarget();
                EdgeType type = edge.getType();
                // Everything that's involved in finalizing this node must now run
                switch (type) {
                    case FINALIZER:
                    case AVOID_STARTING_BEFORE_FINALIZED:
                        markAsMustRun(target);
                        break;
                    default:
                        break;

                }
                // Since the node is now starting, we can unlock nodes that shouldn't have been started before
                switch (type) {
                    case DEPENDENT:
                    case MUST_NOT_RUN_WITH:
                    case MUST_RUN_AFTER:
                    case FINALIZER:
                        return KEEP;
                    case AVOID_STARTING_BEFORE:
                    case AVOID_STARTING_BEFORE_FINALIZED:
                        return REMOVE;
                    default:
                        throw new AssertionError();
                }
            }

            private void markAsMustRun(Node target) {
                switch (target.getState()) {
                    case RUNNABLE:
                    case CANCELLED:
                        target.setState(MUST_RUN);
                    default:
                        // Otherwise it's either failed or already set to MUST_RUN
                        break;
                }
            }
        });
        runningNodes.add(nodeToRun);
        return true;
    }

    private void handleNodeSkipped(Node skippedNode) {
        // TODO Report node status as SKIPPED
        // TODO Handle remaining incoming edges when suspended node is skipped
        graph.removeNodeWithOutgoingEdges(skippedNode, new Action<Edge>() {
            @Override
            public void execute(Edge outgoing) {
                Node target = outgoing.getTarget();
                if (target.getState() == RUNNABLE) {
                    target.setState(CANCELLED);
                }
            }
        });
    }
}
