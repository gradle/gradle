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
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import static org.gradle.internal.scheduler.EdgeType.AVOID_STARTING_BEFORE;
import static org.gradle.internal.scheduler.EdgeType.DEPENDENT;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.KEEP;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.REMOVE;
import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.DEPENDENCY_FAILED;
import static org.gradle.internal.scheduler.NodeState.MUST_RUN;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;
import static org.gradle.internal.scheduler.NodeState.SHOULD_RUN;

public class DefaultScheduler implements Scheduler {
    private static final int MAX_WORKERS = 16;
    private static final int EVENTS_TO_PROCESS_AT_ONCE = 2 * MAX_WORKERS;

    private final BlockingQueue<Event> eventQueue = Queues.newArrayBlockingQueue(MAX_WORKERS);
    private final Set<Node> runningNodes = Sets.newLinkedHashSet();
    private final List<Event> eventsBeingProcessed = Lists.newArrayListWithCapacity(EVENTS_TO_PROCESS_AT_ONCE);
    private final WorkerPool workerPool;
    private final CycleReporter cycleReporter;

    public DefaultScheduler(WorkerPool workerPool, CycleReporter cycleReporter) {
        this.workerPool = workerPool;
        this.cycleReporter = cycleReporter;
    }

    @Override
    public GraphExecutionResult execute(Graph graph, Collection<? extends Node> entryNodes, boolean continueOnFailure, Spec<? super Node> filter) {
        ImmutableList.Builder<Node> filteredNodes = ImmutableList.builder();

        Graph liveGraph = graph.retainLiveNodes(entryNodes, filter, filteredNodes);
        connectFinalizerDependencies(liveGraph);
        enforceEntryNodeOrder(graph, entryNodes);
        liveGraph.breakCycles(cycleReporter);
        markEntryNodesAsShouldRun(graph, entryNodes);

        ImmutableList<Node> liveNodes = liveGraph.getAllNodes();
        ImmutableList.Builder<Node> executedNodes = ImmutableList.builder();
        ImmutableList.Builder<Throwable> failures = ImmutableList.builder();
        executeLiveGraph(liveGraph, continueOnFailure, executedNodes, failures);

        return new GraphExecutionResult(liveNodes, executedNodes.build(), filteredNodes.build(), failures.build());
    }

    private void executeLiveGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
        while (graph.hasNodes()) {
            scheduleWork(graph);
            handleEvents(graph, continueOnFailure, executedNodes, failures);
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

    private void enforceEntryNodeOrder(Graph graph, Collection<? extends Node> entryNodes) {
        Iterator<? extends Node> iEntryNode = entryNodes.iterator();
        if (!iEntryNode.hasNext()) {
            return;
        }
        Node previousNode = iEntryNode.next();
        while (iEntryNode.hasNext()) {
            Node node = iEntryNode.next();
            graph.addEdgeIfAbsent(new Edge(previousNode, node, AVOID_STARTING_BEFORE));
            previousNode = node;
        }
    }

    private static void markEntryNodesAsShouldRun(Graph graph, Iterable<? extends Node> entryNodes) {
        for (Node entryNode : entryNodes) {
            entryNode.setState(SHOULD_RUN);
            graph.walkIncomingEdgesFrom(entryNode, DEPENDENT, new Action<Node>() {
                @Override
                public void execute(Node dependency) {
                    dependency.setState(SHOULD_RUN);
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
                case SHOULD_RUN:
                case MUST_RUN:
                    if (expectAvailableWorkers) {
                        if (!tryRunNode(graph, runningNodes, nodeToRun)) {
                            expectAvailableWorkers = false;
                        }
                    }
                    break;
                case CANCELLED:
                case DEPENDENCY_FAILED:
                    eventQueue.add(new NodeFinishedEvent(nodeToRun));
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
            // TODO Handle execution aborted
            throw new RuntimeException("Handle execution aborted");
        }
        for (Event event : eventsBeingProcessed) {
            System.out.printf("> Handling event %s%n", event);
            event.handle(graph, continueOnFailure, executedNodes, failures);
        }
        eventsBeingProcessed.clear();
    }

    /**
     * Tries to run the given node.
     * @return whether the operation was successful.
     */
    private boolean tryRunNode(Graph graph, Set<Node> runningNodes, final Node nodeToRun) {
        System.out.printf("Checking node %s for conflicts with running nodes %s%n", nodeToRun, runningNodes);
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
                Throwable failure = nodeToRun.execute();
                Event event;
                if (failure == null) {
                    event = new NodeFinishedEvent(nodeToRun);
                } else {
                    event = new NodeFailedEvent(nodeToRun, failure);
                }
                eventQueue.add(event);
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
                    case SHOULD_RUN:
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

    @Override
    public void close() {
        workerPool.close();
    }

    private abstract class Event {
        protected final Node node;

        protected Event(Node node) {
            this.node = node;
        }

        public final void handle(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
            runningNodes.remove(node);
            switch (node.getState()) {
                case RUNNABLE:
                case SHOULD_RUN:
                case MUST_RUN:
                    executedNodes.add(node);
                    break;
                default:
                    break;
            }
            updateGraph(graph, continueOnFailure, failures);
        }

        protected abstract void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures);
    }

    private class NodeFinishedEvent extends Event {
        public NodeFinishedEvent(Node node) {
            super(node);
        }

        @Override
        protected void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures) {
            final NodeState finishedNodeState = node.getState();
            graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                @Override
                public void execute(Edge outgoing) {
                    Node target = outgoing.getTarget();
                    switch (finishedNodeState) {
                        case RUNNABLE:
                        case SHOULD_RUN:
                        case MUST_RUN:
                            if (target.getState() == CANCELLED) {
                                target.setState(RUNNABLE);
                            }
                            break;
                        case CANCELLED:
                            // TODO Handle remaining incoming edges when suspended node is skipped
                            if (target.getState() == RUNNABLE) {
                                target.setState(CANCELLED);
                            }
                            break;
                        case DEPENDENCY_FAILED:
                            // TODO Handle remaining incoming edges when suspended node is skipped
                            switch (outgoing.getType()) {
                                case DEPENDENT:
                                    target.setState(DEPENDENCY_FAILED);
                                    break;
                                case FINALIZER:
                                case AVOID_STARTING_BEFORE_FINALIZED:
                                    if (target.getState() == RUNNABLE) {
                                        target.setState(CANCELLED);
                                    }
                            }
                            break;
                        default:
                            throw new AssertionError();
                    }
                }
            });
        }

        @Override
        public String toString() {
            return String.format("FINISHED %s (%s)", node, node.getState());
        }
    }

    private class NodeFailedEvent extends Event {
        private final Throwable failure;

        public NodeFailedEvent(Node node, Throwable failure) {
            super(node);
            this.failure = failure;
        }

        @Override
        protected void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures) {
            failures.add(failure);
            graph.removeNodeWithOutgoingEdges(node, new Action<Edge>() {
                @Override
                public void execute(Edge outgoing) {
                    Node target = outgoing.getTarget();
                    // TODO Handle remaining incoming edges when suspended node is skipped
                    // TODO Cancel everything if `--continue` is not enabled
                    if (outgoing.getType() == EdgeType.DEPENDENT) {
                        target.setState(DEPENDENCY_FAILED);
                    }
                }
            });

            // Cancel all runnable nodes (including any that is still running) if `--continue` is off
            if (!continueOnFailure) {
                System.out.println("Marking all runnable nodes as cancelled because of failure");
                for (Node candidate : graph.getAllNodes()) {
                    if (candidate.getState() == RUNNABLE || candidate.getState() == SHOULD_RUN) {
                        System.out.printf("Marking %s as cancelled%n", candidate);
                        candidate.setState(CANCELLED);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return String.format("FAILED %s (%s)", node, failure.getClass().getSimpleName());
        }
    }

//    private class NodeSuspendedEvent extends Event {
//        public NodeSuspendedEvent(Node node) {
//            super(node);
//        }
//
//        @Override
//        protected void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures) {
//            graph.processOutgoingEdges(node, new Graph.EdgeAction() {
//                @Override
//                public Graph.EdgeActionResult process(Edge edge) {
//                    return edge.getType() == MUST_NOT_RUN_WITH ? REMOVE : KEEP;
//                }
//            });
//        }
//
//        @Override
//        public String toString() {
//            return String.format("SUSPENDED %s (%s)", node, node.getState());
//        }
//    }
}
