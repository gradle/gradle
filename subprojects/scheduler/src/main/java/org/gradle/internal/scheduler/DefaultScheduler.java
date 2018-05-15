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
import org.gradle.internal.work.WorkerLeaseRegistry;
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
        while (graph.hasNodes()) {
            scheduleWork(graph, nodeExecutor);
            if (cancelled) {
                break;
            }
            handleEvents(graph, continueOnFailure, executedNodes, failures);
        }
    }

    private void scheduleWork(final Graph graph, final NodeExecutor nodeExecutor) {
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (cancellationToken.isCancellationRequested()) {
                    cancelExecution(graph, false);
                    return FINISHED;
                }

                Iterator<Node> iRootNode = graph.getRootNodes().iterator();
                // There are no root nodes to schedule
                if (!iRootNode.hasNext()) {
                    return FINISHED;
                }

                Iterator<TaskExecutorWorker> iWorker = workers.iterator();
                TaskExecutorWorker worker = null;
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
                        eventQueue.add(new NodeFinishedEvent(nodeToRun, null));
                        continue;
                    }

                    // Allocate a worker
                    if (worker == null) {
                        if (!iWorker.hasNext()) {
                            break;
                        }
                        TaskExecutorWorker candidate = iWorker.next();
                        WorkerLease workerLease = candidate.getWorkerLease();
                        if (!workerLease.tryLock()) {
                            continue;
                        }
                        worker = candidate;
                    }

                    // Check if there is a node conflicting with the current one
                    Node conflictingNode = concurrentNodeExecutionCoordinator.findConflictingNode(graph, nodeToRun, runningNodes);
                    if (conflictingNode != null) {
                        // Make sure we don't retry this node until the conflicting node is finished
                        graph.addEdge(new Edge(conflictingNode, MUST_NOT_RUN_WITH, nodeToRun));
                        continue;
                    }

                    // Lock the node
                    ResourceLock nodeLock = concurrentNodeExecutionCoordinator.findLockFor(nodeToRun);
                    if (nodeLock != null && !nodeLock.tryLock()) {
                        continue;
                    }

                    // Run the node
                    try {
                        prepareToRunNode(nodeToRun, graph, runningNodes);
                        worker.run(new NodeExecution(nodeToRun, nodeExecutor, nodeLock));
                    } finally {
                        // TODO Do we need to release the parent lease here?
                        worker.getWorkerLease().unlock();
                        worker = null;
                    }
                }
                if (worker != null) {
                    worker.getWorkerLease().unlock();
                }
                return FINISHED;
            }
        });
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

        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                for (Event event : eventsBeingProcessed) {
                    event.unlockIfNecessary();;
                }
                return FINISHED;
            }
        });

        for (Event event : eventsBeingProcessed) {
            System.out.printf("> Handling event %s%n", event);
            event.handle(graph, continueOnFailure, executedNodes, failures);
        }
        eventsBeingProcessed.clear();
    }

    private static void prepareToRunNode(Node nodeToRun, Graph graph, Set<Node> runningNodes) {
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
        runningNodes.add(nodeToRun);
    }

    @Override
    public void close() {
    }

    private abstract class Event {
        protected final Node node;
        @Nullable
        private final ResourceLock lock;

        protected Event(Node node, @Nullable ResourceLock lock) {
            this.node = node;
            this.lock = lock;
        }

        public void unlockIfNecessary() {
            if (lock != null) {
                lock.unlock();
            }
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
        public NodeFinishedEvent(Node node, @Nullable ResourceLock lock) {
            super(node, lock);
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

    private class NodeFailedEvent extends Event {
        private final Throwable failure;

        public NodeFailedEvent(Node node, @Nullable ResourceLock lock, Throwable failure) {
            super(node, lock);
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

    private static class TaskExecutorWorker implements Runnable {
        private final String name;
        private final BlockingQueue<Runnable> workQueue = Queues.newArrayBlockingQueue(1);
        private final WorkerLease workerLease;
        private Thread thread;

        public TaskExecutorWorker(int index, WorkerLease workerLease) {
            this.name = "Worker #" + (index + 1);
            this.workerLease = workerLease;
        }

        public WorkerLease getWorkerLease() {
            return workerLease;
        }

        public void run(Runnable work) {
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

            AtomicLong busy = new AtomicLong(0);
            Timer totalTimer = Time.startTimer();
            Timer nodeTimer = Time.startTimer();

            while (true) {
                try {
                    Runnable work = workQueue.take();

                    nodeTimer.reset();
                    WorkerLeaseRegistry.WorkerLeaseCompletion leaseCompletion = workerLease.startChild();
                    try {
                        work.run();
                    } finally {
                        leaseCompletion.leaseFinish();
                    }

                    long taskDuration = nodeTimer.getElapsedMillis();
                    busy.addAndGet(taskDuration);
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("{} ({}) completed. Took {}.", work, Thread.currentThread(), TimeFormatting.formatDurationVerbose(taskDuration));
                    }
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

    private class NodeExecution implements Runnable {
        private final Node node;
        private final NodeExecutor nodeExecutor;
        private final ResourceLock lock;

        public NodeExecution(Node node, NodeExecutor nodeExecutor, @Nullable ResourceLock lock) {
            this.node = node;
            this.nodeExecutor = nodeExecutor;
            this.lock = lock;
        }

        @Override
        public void run() {
            Throwable failure = nodeExecutor.execute(node);
            Event event;
            if (failure == null) {
                event = new NodeFinishedEvent(node, lock);
            } else {
                event = new NodeFailedEvent(node, lock, failure);
            }
            eventQueue.add(event);
        }

        @Override
        public String toString() {
            return node.toString();
        }
    }
}
