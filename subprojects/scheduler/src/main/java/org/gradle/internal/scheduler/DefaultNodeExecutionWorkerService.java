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
import org.gradle.api.Transformer;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.Factory;
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
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.resources.ResourceLockState.Disposition.FAILED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;

public class DefaultNodeExecutionWorkerService implements NodeExecutionWorkerService {

    private final ExecutorFactory executorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final ResourceLockCoordinationService coordinationService;
    private final Factory<Path> identityPath;
    private final int executorCount;
    private final List<Worker> workers;
    private final BlockingQueue<Worker> availableWorkers;
    private ManagedExecutor executor;

    public DefaultNodeExecutionWorkerService(
        ParallelismConfiguration parallelismConfiguration,
        ExecutorFactory executorFactory,
        WorkerLeaseService workerLeaseService,
        ResourceLockCoordinationService coordinationService,
        Factory<Path> identityPath
    ) {
        this.executorFactory = executorFactory;
        this.workerLeaseService = workerLeaseService;
        this.coordinationService = coordinationService;
        this.identityPath = identityPath;
        int numberOfParallelExecutors = parallelismConfiguration.getMaxWorkerCount();
        if (numberOfParallelExecutors < 1) {
            throw new IllegalArgumentException("Not a valid number of parallel executors: " + numberOfParallelExecutors);
        }
        this.executorCount = numberOfParallelExecutors;
        this.workers = Lists.newArrayListWithCapacity(numberOfParallelExecutors);
        this.availableWorkers = Queues.newArrayBlockingQueue(numberOfParallelExecutors);
    }

    @Override
    public void start(NodeExecutor nodeExecutor, BlockingQueue<Event> eventQueue) {
        this.executor = executorFactory.create("Task worker for '" + identityPath.create() + "'");
        WorkerLease parentWorkerLease = workerLeaseService.getCurrentWorkerLease();
        for (int index = 0; index < executorCount; index++) {
            Worker worker = new Worker(index, nodeExecutor, coordinationService, parentWorkerLease, availableWorkers, eventQueue);
            workers.add(worker);
            executor.execute(worker);
        }
    }

    @Override
    public void close() {
        for (Worker worker : workers) {
            worker.interrupt();
        }
        executor.stop();
    }

    @Nullable
    @Override
    public NodeExecutionWorker getNextAvailableWorker() {
        return availableWorkers.poll();
    }

    private static class Worker implements NodeExecutionWorker, Runnable {
        private static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
        private final String name;
        private final BlockingQueue<NodeExecution> workQueue = Queues.newArrayBlockingQueue(1);
        private final NodeExecutor nodeExecutor;
        private final ResourceLockCoordinationService coordinationService;
        private final WorkerLease parentLease;
        private final BlockingQueue<Worker> availableWorkers;
        private final BlockingQueue<Event> eventQueue;
        private Thread thread;

        public Worker(int index, NodeExecutor nodeExecutor, ResourceLockCoordinationService coordinationService, WorkerLease parentLease, BlockingQueue<Worker> availableWorkers, BlockingQueue<Event> eventQueue) {
            this.name = "Worker #" + (index + 1);
            this.nodeExecutor = nodeExecutor;
            this.coordinationService = coordinationService;
            this.parentLease = parentLease;
            this.availableWorkers = availableWorkers;
            this.eventQueue = eventQueue;
        }

        @Override
        public void execute(Node node, @Nullable ResourceLock resourceLock) {
            if (!workQueue.offer(new NodeExecution(node, nodeExecutor, resourceLock))) {
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
                availableWorkers.add(this);
                try {
                    NodeExecution work = workQueue.take();

                    nodeTimer.reset();
                    Event event = work.runWithLease(coordinationService, workerLease);
                    eventQueue.add(event);
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

    private static class NodeExecution {
        private final Node node;
        private final NodeExecutor nodeExecutor;
        private final ResourceLock resourceLock;

        public NodeExecution(Node node, NodeExecutor nodeExecutor, @Nullable ResourceLock resourceLock) {
            this.node = node;
            this.nodeExecutor = nodeExecutor;
            this.resourceLock = resourceLock;
        }

        public Event runWithLease(ResourceLockCoordinationService coordinationService, final WorkerLease workerLease) {
            boolean acquiredLocks = coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                @Override
                public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                    if (!workerLease.tryLock()) {
                        System.out.printf("<<* Failed to secure lease %s for %s%n", workerLease, node);
                        return FAILED;
                    }
                    System.out.printf(">>* Acquired lease %s for %s%n", workerLease, node);
                    if (resourceLock != null && !resourceLock.tryLock()) {
                        System.out.printf("<<* Failed to acquire lock %s for %s, releasing lease %s%n", node, resourceLock, workerLease);
                        workerLease.unlock();
                        return FAILED;
                    }
                    System.out.printf(">>* Acquired lock %s for %s%n", resourceLock, node);
                    return FINISHED;
                }
            });

            if (!acquiredLocks) {
                return new NodeSuspendedEvent(node);
            }

            Throwable failure;
            try {
                System.out.printf(">>* Executing %s on %s%n", node, Thread.currentThread().getName());
                failure = nodeExecutor.execute(node);
                System.out.printf("<<* Executed %s, failure: %s%n", node, failure);
            } finally {
                coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                    @Override
                    public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                        if (resourceLock != null) {
                            resourceLock.unlock();
                            System.out.printf("<<* Released lock %s for %s%n", resourceLock, node);
                        }
                        workerLease.unlock();
                        System.out.printf("<<* Released lease %s for %s%n", workerLease, node);
                        return FINISHED;
                    }
                });
            }

            if (failure == null) {
                return new NodeFinishedEvent(node);
            } else {
                return new NodeFailedEvent(node, failure);
            }
        }

        @Override
        public String toString() {
            return node.toString();
        }
    }
}
