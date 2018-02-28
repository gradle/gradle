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

package org.gradle.internal.work;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;

import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock;

/**
 * A queueing mechanism that only executes items once certain conditions are reached.
 */
// TODO This class, DefaultBuildOperationQueue and TaskExecutionPlan have many of the same
// behavior and concerns - we should look for a way to generalize this pattern.
public class DefaultConditionalExecutionQueue<T> implements ConditionalExecutionQueue<T> {
    private enum QueueState {
        Working, Stopped
    }

    private final int maxWorkers;
    private final ResourceLockCoordinationService coordinationService;
    private final ManagedExecutor executor;
    private final Deque<ConditionalExecution<T>> queue = Lists.newLinkedList();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private QueueState queueState = QueueState.Working;
    private int workerCount;

    public DefaultConditionalExecutionQueue(String displayName, int maxWorkers, ExecutorFactory executorFactory, ResourceLockCoordinationService coordinationService) {
        this.maxWorkers = maxWorkers;
        this.executor = executorFactory.create(displayName, maxWorkers);
        this.coordinationService = coordinationService;
    }

    public void submit(ConditionalExecution<T> execution) {
        if (queueState == QueueState.Stopped) {
            throw new IllegalStateException("DefaultConditionalExecutionQueue cannot be reused once it has been stopped.");
        }

        lock.lock();
        try {
            if (workerCount < maxWorkers) {
                executor.submit(new ExecutionRunner());
                workerCount++;
            }

            queue.add(execution);
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            queueState = QueueState.Stopped;
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
        executor.stop();
    }

    private class ExecutionRunner implements Runnable {
        @Override
        public void run() {
            ConditionalExecution operation;
            while ((operation = waitForNextOperation()) != null) {
                runBatch(operation);
            }
            shutDown();
        }

        private ConditionalExecution waitForNextOperation() {
            lock.lock();
            try {
                while (queueState == QueueState.Working && queue.isEmpty()) {
                    try {
                        workAvailable.await();
                    } catch (InterruptedException e) {
                        throw new UncheckedException(e);
                    }
                }

            } finally {
                lock.unlock();
            }

            return getReadyExecution();
        }

        private void runBatch(final ConditionalExecution firstOperation) {
            ConditionalExecution operation = firstOperation;
            while (operation != null) {
                runExecution(operation);
                operation = getReadyExecution();
            }
        }

        /**
         * Gets the next ConditionalExecution object that is ready to be executed.
         */
        private ConditionalExecution getReadyExecution() {
            final AtomicReference<ConditionalExecution> execution = new AtomicReference<ConditionalExecution>();
            coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                @Override
                public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                    if (queue.isEmpty()) {
                        return ResourceLockState.Disposition.FINISHED;
                    }

                    lock.lock();
                    try {
                        Iterator<ConditionalExecution<T>> itr = queue.iterator();
                        while (itr.hasNext()) {
                            ConditionalExecution next = itr.next();
                            if (next.getResourceLock().tryLock()) {
                                execution.set(next);
                                itr.remove();
                                break;
                            }
                        }
                    } finally {
                        lock.unlock();
                    }

                    if (execution.get() == null && !queue.isEmpty()) {
                        return ResourceLockState.Disposition.RETRY;
                    } else {
                        return ResourceLockState.Disposition.FINISHED;
                    }
                }
            });

            return execution.get();
        }

        private void runExecution(ConditionalExecution execution) {
            try {
                execution.getExecution().run();
            } catch (Throwable t) {
                execution.registerFailure(t);
            } finally {
                coordinationService.withStateLock(unlock(execution.getResourceLock()));
                execution.complete();
            }
        }

        private void shutDown() {
            lock.lock();
            try {
                workerCount--;
            } finally {
                lock.unlock();
            }
        }
    }
}
