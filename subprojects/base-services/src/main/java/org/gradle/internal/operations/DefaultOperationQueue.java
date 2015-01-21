/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.gradle.api.Action;
import org.gradle.api.GradleException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 *
 */
class DefaultOperationQueue<T> implements OperationQueue<T>, FutureCallback<Object> {
    private final ListeningExecutorService executor;
    private final Action<? super T> worker;
    private final List<ListenableFuture<?>> workFutures;
    private final List<Throwable> failures;

    private boolean completed;

    DefaultOperationQueue(ListeningExecutorService executor, Action<? super T> worker) {
        this.executor = executor;
        this.worker = worker;
        this.workFutures = Lists.newLinkedList();
        this.failures = Collections.synchronizedList(new ArrayList<Throwable>());
    }

    public void add(final T operation) {
        if (completed) {
            throw new IllegalStateException("OperationQueue cannot be reused once it has started completion.");
        }
        ListenableFuture<?> future = executor.submit(new Operation(operation));
        Futures.addCallback(future, this);
        workFutures.add(future);
    }

    public void waitForCompletion() throws GradleException {
        completed = true;

        // check for completion or failure
        boolean cancelExecution = false;
        for (ListenableFuture<?> future : workFutures) {
            if (!cancelExecution) {
                if (hasFailed(future)) {
                    // cancel remaining operations
                    cancelExecution = true;
                }
            } else {
                future.cancel(false);
            }
        }

        // all operations are complete, check for errors
        synchronized (failures) {
            if (!failures.isEmpty()) {
                // TODO: Multi-cause
                Throwable firstFailure = failures.get(0);
                if (firstFailure instanceof GradleException) {
                    throw (GradleException)firstFailure;
                }
                throw new GradleException("build operation failed", failures.get(0));
            }
        }
    }

    private boolean hasFailed(ListenableFuture<?> future) {
        try {
            future.get();
            return false;
        } catch (InterruptedException e) {
            // Propagate interrupt status
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // Ignore (failures captured in callback)
        }
        return true;
    }

    public void onSuccess(Object result) {
        // result is always null
    }

    public void onFailure(Throwable t) {
        failures.add(t);
    }

    class Operation implements Runnable {
        private final T operation;

        Operation(T operation) {
            this.operation = operation;
        }

        public void run() {
            worker.execute(operation);
        }

        public String toString() {
            return String.format("Worker %s for operation %s", worker, operation);
        }
    }
}
