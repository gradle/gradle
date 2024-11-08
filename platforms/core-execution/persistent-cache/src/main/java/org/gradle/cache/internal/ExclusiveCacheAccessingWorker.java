/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.cache.AsyncCacheAccess;
import org.gradle.cache.ExclusiveCacheAccessCoordinator;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorPolicy;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ExclusiveCacheAccessingWorker implements Runnable, Stoppable, AsyncCacheAccess {
    private final BlockingQueue<Runnable> workQueue;
    private final String displayName;
    private final ExclusiveCacheAccessCoordinator cacheAccess;
    private final long batchWindowMillis;
    private final long maximumLockingTimeMillis;
    private boolean closed;
    private boolean workerCompleted;
    private boolean stopSeen;
    private final CountDownLatch doneSignal = new CountDownLatch(1);
    private final ExecutorPolicy.CatchAndRecordFailures failureHandler = new ExecutorPolicy.CatchAndRecordFailures();

    public ExclusiveCacheAccessingWorker(String displayName, ExclusiveCacheAccessCoordinator cacheAccess) {
        this.displayName = displayName;
        this.cacheAccess = cacheAccess;
        this.batchWindowMillis = 200;
        this.maximumLockingTimeMillis = 5000;
        HeapProportionalCacheSizer heapProportionalCacheSizer = new HeapProportionalCacheSizer();
        int queueCapacity = Math.min(4000, heapProportionalCacheSizer.scaleCacheSize(40000));
        workQueue = new ArrayBlockingQueue<Runnable>(queueCapacity, true);
    }

    @Override
    public void enqueue(Runnable task) {
        addToQueue(task);
    }

    private void addToQueue(Runnable task) {
        if (closed) {
            throw new IllegalStateException("The worker has already been closed. Cannot add more work to queue.");
        }
        try {
            workQueue.put(task);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public <T> T read(Supplier<T> task) {
        FutureTask<T> futureTask = new FutureTask<T>(task::get);
        addToQueue(futureTask);
        try {
            return futureTask.get();
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public synchronized void flush() {
        if (!workerCompleted && !closed) {
            FlushOperationsCommand flushOperationsCommand = new FlushOperationsCommand();
            addToQueue(flushOperationsCommand);
            flushOperationsCommand.await();
        }
        rethrowFailure();
    }

    private void rethrowFailure() {
        failureHandler.onStop();
    }

    private static class FlushOperationsCommand implements Runnable {
        private CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void run() {
        }

        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        public void completed() {
            latch.countDown();
        }
    }

    @Override
    public void run() {
        try {
            Thread currentThread = Thread.currentThread();
            currentThread.setName("Cache worker for " + displayName);

            while (!currentThread.isInterrupted() && !stopSeen) {
                try {
                    Runnable runnable = takeFromQueue();
                    Class<? extends Runnable> runnableClass = runnable.getClass();
                    if (runnableClass == ShutdownOperationsCommand.class) {
                        // not holding the cache lock, can stop now
                        stopSeen = true;
                        break;
                    } else if (runnableClass == FlushOperationsCommand.class) {
                        // not holding the cache lock, flush is done so notify flush thread and continue
                        FlushOperationsCommand flushOperationsCommand = (FlushOperationsCommand) runnable;
                        flushOperationsCommand.completed();
                    } else {
                        // need to run operation under cache lock
                        flushOperations(runnable);
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } catch (Throwable t) {
            failureHandler.onFailure("Failed to execute cache operations on " + displayName, t);
        } finally {
            // Notify any waiting flush threads that the worker is done, possibly with a failure
            List<Runnable> runnables = new ArrayList<Runnable>();
            workQueue.drainTo(runnables);
            for (Runnable runnable : runnables) {
                if (runnable instanceof FlushOperationsCommand) {
                    FlushOperationsCommand flushOperationsCommand = (FlushOperationsCommand) runnable;
                    flushOperationsCommand.completed();
                }
            }
            workerCompleted = true;
            doneSignal.countDown();
        }
    }

    private Runnable takeFromQueue() throws InterruptedException {
        return workQueue.take();
    }

    private void flushOperations(final Runnable updateOperation) {
        final List<FlushOperationsCommand> flushOperations = new ArrayList<FlushOperationsCommand>();
        try {
            cacheAccess.useCache(new Runnable() {
                @Override
                public void run() {
                    CountdownTimer timer = Time.startCountdownTimer(maximumLockingTimeMillis, TimeUnit.MILLISECONDS);
                    if (updateOperation != null) {
                        failureHandler.onExecute(updateOperation);
                    }
                    Runnable otherOperation;
                    try {
                        while ((otherOperation = workQueue.poll(batchWindowMillis, TimeUnit.MILLISECONDS)) != null) {
                            failureHandler.onExecute(otherOperation);
                            final Class<? extends Runnable> runnableClass = otherOperation.getClass();
                            if (runnableClass == FlushOperationsCommand.class) {
                                flushOperations.add((FlushOperationsCommand) otherOperation);
                            }
                            if (runnableClass == ShutdownOperationsCommand.class) {
                                stopSeen = true;
                            }
                            if (runnableClass == ShutdownOperationsCommand.class
                                    || runnableClass == FlushOperationsCommand.class
                                    || timer.hasExpired()) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            });
        } finally {
            for (FlushOperationsCommand flushOperation : flushOperations) {
                flushOperation.completed();
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!closed && !workerCompleted) {
            closed = true;
            try {
                workQueue.put(new ShutdownOperationsCommand());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                doneSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        rethrowFailure();
    }

    private static class ShutdownOperationsCommand implements Runnable {
        @Override
        public void run() {
            // do nothing
        }
    }
}
