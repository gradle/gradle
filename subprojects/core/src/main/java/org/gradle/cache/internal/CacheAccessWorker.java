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

import org.gradle.api.internal.cache.HeapProportionalCacheSizer;
import org.gradle.cache.CacheAccess;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorPolicy;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Timers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

class CacheAccessWorker implements Runnable, Stoppable, AsyncCacheAccess {
    private final BlockingQueue<Runnable> workQueue;
    private final String displayName;
    private final CacheAccess cacheAccess;
    private final long batchWindowMillis;
    private final long maximumLockingTimeMillis;
    private boolean closed;
    private boolean workerCompleted;
    private boolean stopSeen;
    private final CountDownLatch doneSignal = new CountDownLatch(1);
    private final ExecutorPolicy.CatchAndRecordFailures failureHandler = new ExecutorPolicy.CatchAndRecordFailures();

    CacheAccessWorker(String displayName, CacheAccess cacheAccess) {
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
        addToQueue(AsyncCacheAccessRunnable.wrapWhenContextIsUsed(task));
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

    public <T> T read(final Factory<T> task) {
        FutureTask<T> futureTask = AsyncCacheAccessFutureTask.wrapWhenContextIsUsed(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return task.create();
            }
        });
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
            while (!Thread.currentThread().isInterrupted() && !stopSeen) {
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
                    CountdownTimer timer = Timers.startTimer(maximumLockingTimeMillis, TimeUnit.MILLISECONDS);
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

    public synchronized void stop() {
        if (!closed && !workerCompleted) {
            closed = true;
            try {
                workQueue.put(new ShutdownOperationsCommand());
            } catch (InterruptedException e) {
                // ignore
            }
            try {
                doneSignal.await();
            } catch (InterruptedException e) {
                // ignore
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

    // passes ThreadLocal context from requesting thread over to worker thread
    private static class AsyncCacheAccessFutureTask<V> extends FutureTask<V> implements Runnable {
        private final AsyncCacheAccessContext context;

        private AsyncCacheAccessFutureTask(Callable<V> callable, AsyncCacheAccessContext context) {
            super(callable);
            this.context = context;
        }

        static <V> FutureTask<V> wrapWhenContextIsUsed(Callable<V> callable) {
            AsyncCacheAccessContext context = AsyncCacheAccessContext.copyOfCurrent();
            if (context != null) {
                return new AsyncCacheAccessFutureTask<V>(callable, context);
            } else {
                return new FutureTask<V>(callable);
            }
        }

        @Override
        public void run() {
            try {
                AsyncCacheAccessContext.apply(context);
                super.run();
            } finally {
                AsyncCacheAccessContext.remove();
            }
        }
    }

    // makes a copy of the ThreadLocal context and passes it from the requesting thread over to worker thread
    private static class AsyncCacheAccessRunnable implements Runnable {
        private final Runnable delegate;
        private final AsyncCacheAccessContext context;

        private AsyncCacheAccessRunnable(Runnable delegate, AsyncCacheAccessContext context) {
            this.delegate = delegate;
            this.context = context;
        }

        static Runnable wrapWhenContextIsUsed(Runnable delegate) {
            AsyncCacheAccessContext context = AsyncCacheAccessContext.copyOfCurrent();
            if (context != null) {
                return new AsyncCacheAccessRunnable(delegate, context);
            } else {
                return delegate;
            }
        }

        @Override
        public void run() {
            try {
                AsyncCacheAccessContext.apply(context);
                delegate.run();
            } finally {
                AsyncCacheAccessContext.remove();
            }
        }
    }
}
