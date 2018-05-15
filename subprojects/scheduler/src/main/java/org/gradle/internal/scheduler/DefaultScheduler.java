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
import org.gradle.api.BuildCancelledException;
import org.gradle.api.Transformer;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.internal.time.Timer;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.resources.ResourceLockState.Disposition.FAILED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF;
import static org.gradle.internal.scheduler.EdgeType.MUST_NOT_RUN_WITH;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.KEEP;
import static org.gradle.internal.scheduler.Graph.EdgeActionResult.REMOVE;
import static org.gradle.internal.scheduler.NodeState.CANCELLED;
import static org.gradle.internal.scheduler.NodeState.DEPENDENCY_FAILED;
import static org.gradle.internal.scheduler.NodeState.MUST_RUN;
import static org.gradle.internal.scheduler.NodeState.RUNNABLE;
import static org.gradle.internal.scheduler.NodeState.SHOULD_RUN;

public class DefaultScheduler implements Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultScheduler.class);
    private static final int MAX_WORKERS = 16;
    private static final int EVENTS_TO_PROCESS_AT_ONCE = 2 * MAX_WORKERS;

    private final BlockingQueue<Event> eventQueue = Queues.newArrayBlockingQueue(MAX_WORKERS);
    private final Set<Node> runningNodes = Sets.newLinkedHashSet();
    private final List<Event> eventsBeingProcessed = Lists.newArrayListWithCapacity(EVENTS_TO_PROCESS_AT_ONCE);
    private final int executorCount;
    private final ExecutorFactory executorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final BuildCancellationToken cancellationToken;
    private final ResourceLockCoordinationService coordinationService;
    private final ConcurrentNodeExecutionCoordinator concurrentNodeExecutionCoordinator;
    private final List<TaskExecutorWorker> workers;
    private final BlockingQueue<TaskExecutorWorker> availableWorkers;
    private boolean cancelled;

    public DefaultScheduler(
            ParallelismConfiguration parallelismConfiguration,
            ExecutorFactory executorFactory,
            WorkerLeaseService workerLeaseService,
            BuildCancellationToken cancellationToken,
            ResourceLockCoordinationService coordinationService,
            ConcurrentNodeExecutionCoordinator concurrentNodeExecutionCoordinator
    ) {
        this.executorFactory = executorFactory;
        this.workerLeaseService = workerLeaseService;
        this.cancellationToken = cancellationToken;
        this.coordinationService = coordinationService;
        this.concurrentNodeExecutionCoordinator = concurrentNodeExecutionCoordinator;
        int numberOfParallelExecutors = parallelismConfiguration.getMaxWorkerCount();
        if (numberOfParallelExecutors < 1) {
            throw new IllegalArgumentException("Not a valid number of parallel executors: " + numberOfParallelExecutors);
        }

        this.executorCount = numberOfParallelExecutors;
        this.workers = Lists.newArrayListWithCapacity(numberOfParallelExecutors);
        this.availableWorkers = Queues.newArrayBlockingQueue(numberOfParallelExecutors);
    }

    @Override
    public GraphExecutionResult execute(Graph graph, Collection<? extends Node> entryNodes, boolean continueOnFailure, NodeExecutor nodeExecutor) {
        // TODO Get name via Gradle.findIdentityPath()
        ManagedExecutor executor = executorFactory.create("Task worker for '" + "gradle" + "'");
        try {
            WorkerLease parentWorkerLease = workerLeaseService.getCurrentWorkerLease();
            for (int index = 0; index < executorCount; index++) {
                WorkerLease childLease = parentWorkerLease.createChild();
                TaskExecutorWorker worker = new TaskExecutorWorker(index, childLease);
                workers.add(worker);
                availableWorkers.add(worker);
                executor.execute(worker);
            }

            ImmutableList.Builder<Node> executedNodes = ImmutableList.builder();
            ImmutableList.Builder<Throwable> failures = ImmutableList.builder();

            execute(graph, nodeExecutor, continueOnFailure, executedNodes, failures);
            if (cancelled) {
                failures.add(new BuildCancelledException());
            }
            return new GraphExecutionResult(executedNodes.build(), failures.build());
        } finally {
            for (TaskExecutorWorker worker : workers) {
                worker.interrupt();
            }
            executor.stop();
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

    private void execute(final Graph graph, final NodeExecutor nodeExecutor, final boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
        boolean expectAvailableWorkers = true;
        while (graph.hasNodes()) {
            if (expectAvailableWorkers) {
                scheduleWork(graph, nodeExecutor);
            }
            if (cancelled) {
                break;
            }
            expectAvailableWorkers = handleEvents(graph, continueOnFailure, executedNodes, failures);
        }
    }

    private void scheduleWork(final Graph graph, final NodeExecutor nodeExecutor) {
        final ImmutableList<Node> rootNodes = graph.getRootNodes();
        System.out.printf(">> Scheduling root nodes: %s%n", rootNodes);

        if (cancellationToken.isCancellationRequested()) {
            cancelExecution(graph, false);
            return;
        }

        Iterator<Node> iRootNode = rootNodes.iterator();
        // There are no root nodes to schedule
        if (!iRootNode.hasNext()) {
            return;
        }

        while (true) {
            // Find a root node to allocate
            if (!iRootNode.hasNext()) {
                break;
            }
            Node nodeToRun = iRootNode.next();
            if (runningNodes.contains(nodeToRun)) {
                continue;
            }

            // TODO These could be handled outside of the coordination-service lock
            if (!nodeToRun.getState().isExecutable()) {
                enqueue(new NodeFinishedEvent(nodeToRun));
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
            TaskExecutorWorker worker = availableWorkers.poll();
            if (worker == null) {
                break;
            }


            // Run the node
            System.out.printf(">> Trying to run %s...%n", nodeToRun);
            // TODO Don't do this for revived nodes that have already been prepared once?
            prepareToRunNode(nodeToRun, graph);
            runningNodes.add(nodeToRun);
            ResourceLock nodeLock = concurrentNodeExecutionCoordinator.findLockFor(nodeToRun);
            worker.run(new NodeExecution(nodeToRun, nodeExecutor, nodeLock));
        }
    }

    private boolean handleEvents(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
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

        boolean expectAvailableWorkers = false;
        for (Event event : eventsBeingProcessed) {
            System.out.printf(">> Handling event %s%n", event);
            expectAvailableWorkers |= event.handle(graph, continueOnFailure, executedNodes, failures);
        }
        eventsBeingProcessed.clear();
        System.out.printf(">> Nodes after handling events: %s, expect workers: %s%n", graph.getAllNodes(), expectAvailableWorkers);
        return expectAvailableWorkers;
    }

    private static void prepareToRunNode(Node nodeToRun, Graph graph) {
        graph.processOutgoingEdges(nodeToRun, new Graph.EdgeAction() {
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

    public void enqueue(Event event) {
        System.out.printf(">> Enqueuing event %s (%s)%n", event, Thread.currentThread().getName());
        eventQueue.add(event);
    }

    @Override
    public void close() {
    }

    private abstract class Event {
        protected final Node node;

        protected Event(Node node) {
            this.node = node;
        }

        public boolean handle(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
            runningNodes.remove(node);
            updateGraph(graph, continueOnFailure, failures);
            return true;
        }

        protected abstract void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures);
    }

    private abstract class AbstractNodeCompletionEvent extends Event {
        protected AbstractNodeCompletionEvent(Node node) {
            super(node);
        }

        @Override
        public boolean handle(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
            switch (node.getState()) {
                case RUNNABLE:
                case SHOULD_RUN:
                case MUST_RUN:
                    executedNodes.add(node);
                    break;
                default:
                    break;
            }
            return super.handle(graph, continueOnFailure, executedNodes, failures);
        }
    }

    private class NodeFinishedEvent extends AbstractNodeCompletionEvent {
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
                                case DEPENDENCY_OF:
                                    target.setState(DEPENDENCY_FAILED);
                                    break;
                                case FINALIZED_BY:
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

    private class NodeFailedEvent extends AbstractNodeCompletionEvent {
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
                    if (outgoing.getType() == DEPENDENCY_OF) {
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

    private class NodeSuspendedEvent extends Event {
        public NodeSuspendedEvent(Node node) {
            super(node);
        }

        @Override
        public boolean handle(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Node> executedNodes, ImmutableList.Builder<Throwable> failures) {
            super.handle(graph, continueOnFailure, executedNodes, failures);
            return false;
        }

        @Override
        protected void updateGraph(Graph graph, boolean continueOnFailure, ImmutableList.Builder<Throwable> failures) {
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

    private class TaskExecutorWorker implements Runnable {
        private final String name;
        private final BlockingQueue<NodeExecution> workQueue = Queues.newArrayBlockingQueue(1);
        private final WorkerLease parentLease;
        private Thread thread;

        public TaskExecutorWorker(int index, WorkerLease parentLease) {
            this.name = "Worker #" + (index + 1);
            this.parentLease = parentLease;
        }

        public WorkerLease getParentLease() {
            return parentLease;
        }

        public void run(NodeExecution work) {
            if (!workQueue.offer(work)) {
                throw new IllegalStateException("There's already work being done by " + this);
            }
        }

        // TODO Handle this more elegantly
        public void interrupt() {
            Thread thread = this.thread;
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void run() {
            this.thread = Thread.currentThread();
            WorkerLease workerLease = parentLease.createChild();

            AtomicLong busy = new AtomicLong(0);
            Timer totalTimer = Time.startTimer();
            Timer nodeTimer = Time.startTimer();

            while (true) {
                try {
                    NodeExecution work = workQueue.take();

                    nodeTimer.reset();
                    work.runWithLease(workerLease);
                    long taskDuration = nodeTimer.getElapsedMillis();
                    busy.addAndGet(taskDuration);
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("{} ({}) completed. Took {}.", work, Thread.currentThread(), TimeFormatting.formatDurationVerbose(taskDuration));
                    }

                    availableWorkers.add(this);
                } catch (InterruptedException e) {
                    break;
                }
            }

            long total = totalTimer.getElapsedMillis();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), TimeFormatting.formatDurationVerbose(busy.get()), TimeFormatting.formatDurationVerbose(total - busy.get()));
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class NodeExecution {
        private final Node node;
        private final NodeExecutor nodeExecutor;
        private final ResourceLock resourceLock;

        public NodeExecution(Node node, NodeExecutor nodeExecutor, @Nullable ResourceLock resourceLock) {
            this.node = node;
            this.nodeExecutor = nodeExecutor;
            this.resourceLock = resourceLock;
        }

        public void runWithLease(final WorkerLease workerLease) {
            boolean acquiredLocks = coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                @Override
                public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                    if (!workerLease.tryLock()) {
                        System.out.printf("<< Failed to secure lease %s for %s%n", workerLease, node);
                        return FAILED;
                    }
                    System.out.printf(">> Acquired lease %s for %s%n", workerLease, node);
                    if (resourceLock != null && !resourceLock.tryLock()) {
                        System.out.printf("<< Failed to acquire lock %s for %s, releasing lease %s%n", node, resourceLock, workerLease);
                        workerLease.unlock();
                        return FAILED;
                    }
                    System.out.printf(">> Acquired lock %s for %s%n", resourceLock, node);
                    return FINISHED;
                }
            });

            Event event;
            if (!acquiredLocks) {
                event = new NodeSuspendedEvent(node);
            } else {
                Throwable failure;
                try {
                    System.out.printf(">> Executing %s%n", node);
                    failure = nodeExecutor.execute(node);
                    System.out.printf("<< Executed %s, failure: %s%n", node, failure);
                } finally {
                    coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                        @Override
                        public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                            if (resourceLock != null) {
                                resourceLock.unlock();
                                System.out.printf("<< Released lock %s for %s%n", resourceLock, node);
                            }
                            workerLease.unlock();
                            System.out.printf("<< Released lease %s for %s%n", workerLease, node);
                            return FINISHED;
                        }
                    });
                }

                if (failure == null) {
                    event = new NodeFinishedEvent(node);
                } else {
                    event = new NodeFailedEvent(node, failure);
                }
            }
            enqueue(event);
        }

        @Override
        public String toString() {
            return node.toString();
        }
    }
}
