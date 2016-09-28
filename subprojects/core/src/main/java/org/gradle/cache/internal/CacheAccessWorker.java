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

import org.gradle.api.GradleException;
import org.gradle.cache.CacheAccess;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class CacheAccessWorker implements Runnable, Stoppable, AsyncCacheAccess {
    private final BlockingQueue<Runnable> workQueue;
    private final CacheAccess cacheAccess;
    private final long batchWindow;
    private final long maximumLockingTimeMillis;
    private boolean closed;
    private boolean running;
    private final CountDownLatch doneSignal = new CountDownLatch(1);
    private AtomicReference<Throwable> failureHolder = new AtomicReference<Throwable>(null);
    private Set<Runnable> pendingFlushOperations = Collections.synchronizedSet(new LinkedHashSet<Runnable>());
    private final Object failureLock = new Object();

    CacheAccessWorker(CacheAccess cacheAccess, int queueCapacity, long batchWindow, long maximumLockingTimeMillis) {
        this.cacheAccess = cacheAccess;
        this.batchWindow = batchWindow;
        this.maximumLockingTimeMillis = maximumLockingTimeMillis;
        workQueue = new ArrayBlockingQueue<Runnable>(queueCapacity);
    }

    public void enqueue(Runnable task) {
        if (closed) {
            throw new IllegalStateException("The worker has already been closed. Cannot add more work to queue.");
        }
        try {
            workQueue.put(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public <T> T read(Callable<T> task) {
        FutureTask<T> futureTask = new FutureTask<T>(task);
        enqueue(futureTask);
        try {
            return futureTask.get();
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public synchronized void flush() {
        rethrowFailure();
        if (running && !closed) {
            FlushOperationsCommand flushOperationsCommand = new FlushOperationsCommand();
            try {
                synchronized (failureLock) {
                    pendingFlushOperations.add(flushOperationsCommand);
                    enqueue(flushOperationsCommand);
                    rethrowFailure();
                }
                synchronized (failureLock) {
                    rethrowFailure(); // 2nd time to handle race-condition
                }
                flushOperationsCommand.await();
            } finally {
                pendingFlushOperations.remove(flushOperationsCommand);
            }
            rethrowFailure();
        }
    }

    private void rethrowFailure() {
        Throwable failure = failureHolder.get();
        if (failure != null) {
            if (failure instanceof GradleException) {
                throw GradleException.class.cast(failure);
            } else {
                throw new GradleException("Cannot flush cache operations.", failure);
            }
        }
    }

    private static class FlushOperationsCommand implements Runnable {
        private CountDownLatch latch = new CountDownLatch(2);

        @Override
        public void run() {
            latch.countDown();
        }

        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        running = true;
        try {
            while (!Thread.currentThread().isInterrupted() && !closed) {
                try {
                    final Runnable runnable = takeFromQueue();
                    final Class<? extends Runnable> runnableClass = runnable.getClass();
                    if (runnableClass == ShutdownOperationsCommand.class) {
                        break;
                    } else if (runnableClass == FlushOperationsCommand.class) {
                        // not holding the cache's lock, call operation twice to trigger latch
                        runnable.run();
                        runnable.run();
                    } else {
                        flushOperations(runnable, batchWindow, maximumLockingTimeMillis);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!workQueue.isEmpty()) {
                flushOperations(null, 0L, -1L);
            }
        } catch (Throwable t) {
            synchronized (failureLock) {
                failureHolder.set(t);
                for (Runnable runnable : pendingFlushOperations) {
                    // call twice to make sure that latch trips
                    runnable.run();
                    runnable.run();
                }
            }
        } finally {
            closed = true;
            running = false;
            doneSignal.countDown();
        }
    }

    // separate method for testing by subclassing
    protected Runnable takeFromQueue() throws InterruptedException {
        return workQueue.take();
    }

    private void flushOperations(final Runnable updateOperation, final long timeoutMillis, final long maximumLockingTimeMillis) {
        final List<Runnable> flushOperations = new ArrayList<Runnable>();
        try {
            cacheAccess.useCache("CacheAccessWorker flushing operations", new Runnable() {
                @Override
                public void run() {
                    long lockingStarted = System.currentTimeMillis();
                    if (updateOperation != null) {
                        updateOperation.run();
                    }
                    Runnable otherOperation;
                    try {
                        while ((otherOperation = workQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS)) != null) {
                            otherOperation.run();
                            final Class<? extends Runnable> runnableClass = otherOperation.getClass();
                            if (runnableClass == FlushOperationsCommand.class) {
                                flushOperations.add(otherOperation);
                            }
                            if (runnableClass == ShutdownOperationsCommand.class
                                    || runnableClass == FlushOperationsCommand.class
                                    || maximumLockingTimeMillis > 0L && System.currentTimeMillis() - lockingStarted > maximumLockingTimeMillis) {
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        } finally {
            for (Runnable flushOperation : flushOperations) {
                // call 2nd time to trigger latch
                // this tells the flusher thread that the "useCache" block has exited.
                flushOperation.run();
            }
        }
    }

    public synchronized void stop() {
        if (!closed && running) {
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
    }

    private static class ShutdownOperationsCommand implements Runnable {
        @Override
        public void run() {
            // do nothing
        }
    }
}
