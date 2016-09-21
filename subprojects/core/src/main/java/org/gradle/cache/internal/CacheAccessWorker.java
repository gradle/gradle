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

import org.gradle.cache.CacheAccess;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

class CacheAccessWorker implements Runnable, Stoppable, AsyncCacheAccess {
    private final BlockingQueue<Runnable> workQueue;
    private final CacheAccess cacheAccess;
    private final long batchWindow;
    private final long maximumLockingTimeMillis;
    private boolean closed;
    private boolean running;
    private final CountDownLatch doneSignal = new CountDownLatch(1);

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
    public void run() {
        running = true;
        try {
            while (!Thread.currentThread().isInterrupted() && !closed) {
                try {
                    final Runnable runnable = workQueue.take();
                    if (runnable.getClass() == ShutdownOperationsCommand.class) {
                        break;
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
        } finally {
            closed = true;
            running = false;
            doneSignal.countDown();
        }
    }

    private void flushOperations(final Runnable updateOperation, final long timeoutMillis, final long maximumLockingTimeMillis) {
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
                        if (otherOperation.getClass() == ShutdownOperationsCommand.class
                                || maximumLockingTimeMillis > 0L && System.currentTimeMillis() - lockingStarted > maximumLockingTimeMillis) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
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
