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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A queueing mechanism that only executes items once certain conditions are reached.
 */
// TODO This class, DefaultBuildOperationQueue and ExecutionPlan have many of the same
// behavior and concerns - we should look for a way to generalize this pattern.
public class DefaultConditionalExecutionQueue<T> implements WorkerThreadPool, ConditionalExecutionQueue<T> {
    public static final int KEEP_ALIVE_TIME_MS = 2000;

    private enum QueueState {
        Working, Stopped
    }

    private final WorkerLeaseService workerLeaseService;
    private final ManagedExecutor executor;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition workAvailable = lock.newCondition();
    private QueueState queueState = QueueState.Working;
    @GuardedBy("lock")
    private final WorkerThreadPoolHelper<ConditionalExecution<T>> helper;

    public DefaultConditionalExecutionQueue(String displayName, WorkerLimits workerLimits, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
        this.executor = executorFactory.create(displayName);
        this.helper = new WorkerThreadPoolHelper<>(workerLimits, () -> {
            Future<?> ignored = executor.submit(new ExecutionRunner());
        });

        executor.setKeepAlive(KEEP_ALIVE_TIME_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void submit(ConditionalExecution<T> execution) {
        if (queueState == QueueState.Stopped) {
            throw new IllegalStateException("DefaultConditionalExecutionQueue cannot be reused once it has been stopped.");
        }

        lock.lock();
        try {
            helper.submitWork(execution);
            workAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyBlockingWorkStarting() {
        lock.lock();
        try {
            helper.notifyBlockingWorkStarting();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void notifyBlockingWorkFinished() {
        lock.lock();
        try {
            helper.notifyBlockingWorkFinished();
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

    /**
     * ExecutionRunners process items from the queue until there are no items left, at which point it will either wait for
     * new items to arrive (if there are less than max workers threads running) or exit, finishing the thread.
     */
    private class ExecutionRunner implements Runnable {
        @Override
        public void run() {
            workerLeaseService.setOwningThreadPool(DefaultConditionalExecutionQueue.this);
            try {
                ConditionalExecution<?> operation;
                while ((operation = waitForNextOperation()) != null) {
                    runBatch(operation);
                }
            } finally {
                workerLeaseService.setOwningThreadPool(null);
                shutDown();
            }
        }

        private @Nullable ConditionalExecution<?> waitForNextOperation() {
            lock.lock();
            try {
                while (queueState == QueueState.Working && helper.shouldWorkerKeepWaiting()) {
                    try {
                        workAvailable.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                return helper.pollWork();
            } finally {
                lock.unlock();
            }
        }

        /**
         * Run executions until there are none ready to be executed.
         */
        private void runBatch(final ConditionalExecution<?> firstOperation) {
            workerLeaseService.runAsWorkerThread(new Runnable() {
                @Override
                public void run() {
                    ConditionalExecution<?> operation = firstOperation;
                    while (operation != null) {
                        runExecution(operation);

                        lock.lock();
                        try {
                            operation = helper.pollWork();
                        } finally {
                            lock.unlock();
                        }
                    }
                }
            });
        }

        /**
         * Executes a conditional execution and then releases its resource lock.
         */
        private void runExecution(final ConditionalExecution<?> execution) {
            try {
                execution.getExecution().run();
            } finally {
                execution.complete();
            }
        }

        private void shutDown() {
            lock.lock();
            try {
                helper.notifyWorkerFinished();
            } finally {
                lock.unlock();
            }
        }
    }
}
