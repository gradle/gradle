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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static org.gradle.internal.scheduler.EdgeType.MUST_NOT_RUN_WITH;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.KEEP;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.REMOVE;
import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.FAILED;
import static org.gradle.internal.scheduler.NodeState.MUST_RUN;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;

public class DefaultScheduler implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);
    private static final int MAX_WORKERS = 16;
    private static final int EVENTS_TO_PROCESS_AT_ONCE = 2 * MAX_WORKERS;

    private final BlockingQueue<Event> eventQueue = Queues.newArrayBlockingQueue(MAX_WORKERS);
    private final Set<Node> runningNodes = Sets.newLinkedHashSet();
    private final List<Event> eventsBeingProcessed = Lists.newArrayListWithCapacity(EVENTS_TO_PROCESS_AT_ONCE);
    private final boolean continueOnFailure;
    private final WorkerPool workerPool;
    private final CycleReporter cycleReporter;

    public DefaultScheduler(boolean continueOnFailure, WorkerPool workerPool, CycleReporter cycleReporter) {
        this.continueOnFailure = continueOnFailure;
        this.workerPool = workerPool;
        this.cycleReporter = cycleReporter;
    }

    @Override
    public void execute(Graph graph, Collection<Node> entryNodes) {
        Graph liveGraph = graph.retainLiveNodes(entryNodes);
        connectFinalizerDependencies(liveGraph);
        liveGraph.breakCycles(cycleReporter);
        executeLiveGraph(liveGraph);
    }

    private void executeLiveGraph(Graph graph) {
        while (graph.hasNodes()) {
            scheduleWork(graph);
            handleEvents(graph);
        }
    }

    private void connectFinalizerDependencies(final Graph graph) {
        for (Edge edge : graph.getAllEdges()) {
            if (edge.getType() != EdgeType.FINALIZER) {
                continue;
            }
            final Set<Edge> edgesAddedFromFinalized = Sets.newHashSet();
            final Node finalized = edge.getSource();
            Node finalizer = edge.getTarget();
            graph.walkIncomingEdgesFrom(finalizer, EdgeType.DEPENDENT, new Action<Node>() {
                @Override
                public void execute(Node finalizerDependency) {
                    Edge finalizerDependencyConstraint = new Edge(finalized, finalizerDependency, EdgeType.AVOID_STARTING_BEFORE_FINALIZED);
                    if (edgesAddedFromFinalized.add(finalizerDependencyConstraint)) {
                        graph.addEdge(finalizerDependencyConstraint);
                    }
                }
            });
        }
    }

    private void scheduleWork(Graph graph) {
        boolean expectAvailableWorkers = true;
        for (Node nodeToRun : graph.getRootNodes()) {
            if (runningNodes.contains(nodeToRun)) {
                continue;
            }
            switch (nodeToRun.getState()) {
                case RUNNABLE:
                case MUST_RUN:
                    if (expectAvailableWorkers) {
                        if (!tryRunNode(graph, runningNodes, nodeToRun)) {
                            expectAvailableWorkers = false;
                        }
                    }
                    break;
                case CANCELLED:
                case FAILED:
                    handleNodeSkipped(graph, nodeToRun);
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    private void handleEvents(Graph graph) {
        try {
            // Wait for at least one event to arrive
            Event event = eventQueue.take();
            eventsBeingProcessed.add(event);
            // Make sure we take all available events
            eventQueue.drainTo(eventsBeingProcessed, EVENTS_TO_PROCESS_AT_ONCE - 1);
        } catch (InterruptedException e) {
            // TODO Handle execution aborted
            throw new RuntimeException("Handle execution aborted");
        }
        for (Event event : eventsBeingProcessed) {
            System.out.printf("> Handling event %s%n", event);
            event.handle(graph);
        }
        eventsBeingProcessed.clear();
    }

    /**
     * Tries to run the given node.
     * @return whether the operation was successful.
     */
    private boolean tryRunNode(Graph graph, Set<Node> runningNodes, final Node nodeToRun) {
        for (Node runningNode : runningNodes) {
            if (!runningNode.canExecuteInParallelWith(nodeToRun)) {
                System.out.printf("> Cannot run node %s with %s%n", nodeToRun, runningNode);
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

    private static void handleNodeSkipped(Graph graph, Node skippedNode) {
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

    @Override
    public void close() {
        workerPool.close();
    }

    private abstract class Event {
        protected final Node node;

        protected Event(Node node) {
            this.node = node;
        }

        public final void handle(Graph graph) {
            runningNodes.remove(node);
            handleGraphChanges(graph);
        }

        protected abstract void handleGraphChanges(Graph graph);
    }

    private class NodeFinishedEvent extends Event {
        public NodeFinishedEvent(Node node) {
            super(node);
        }

        @Override
        protected void handleGraphChanges(Graph graph) {
            switch (node.getState()) {
                case RUNNABLE:
                case MUST_RUN:
                    graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                        @Override
                        public void execute(Edge edge) {
                            Node target = edge.getTarget();
                            if (target.getState() == CANCELLED) {
                                target.setState(RUNNABLE);
                            }
                        }
                    });
                    break;
                case CANCELLED:
                    // TODO Handle remaining incoming edges when suspended node is skipped
                    graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                        @Override
                        public void execute(Edge outgoing) {
                            Node target = outgoing.getTarget();
                            if (target.getState() == RUNNABLE) {
                                target.setState(CANCELLED);
                            }
                        }
                    });
                    break;
                case FAILED:
                    // TODO Cancel everything if `--continue` is not enabled
                    graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                        @Override
                        public void execute(Edge outgoing) {
                            if (outgoing.getType() == EdgeType.DEPENDENT) {
                                outgoing.getTarget().setState(FAILED);
                            }
                        }
                    });
                    break;
            }
        }

        @Override
        public String toString() {
            return String.format("FINISHED %s (%s)", node, node.getState());
        }
    }

    private class NodeSuspendedEvent extends Event {
        public NodeSuspendedEvent(Node node) {
            super(node);
        }

        @Override
        protected void handleGraphChanges(Graph graph) {
            graph.processOutgoingEdges(node, new Graph.EdgeAction() {
                @Override
                public Graph.EdgeActionResult process(Edge edge) {
                    return edge.getType() == MUST_NOT_RUN_WITH ? REMOVE : KEEP;
                }
            });
        }

        @Override
        public String toString() {
            return String.format("SUSPENDED %s (%s)", node, node.getState());
        }
    }
}
