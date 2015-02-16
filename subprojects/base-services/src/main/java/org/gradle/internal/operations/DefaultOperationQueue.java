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
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.*;
import org.gradle.api.Action;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

class DefaultOperationQueue<T> implements OperationQueue<T> {
    private final ListeningExecutorService executor;
    private final Action<? super T> worker;

    private final List<ListenableFuture> operations;
    private final Set<Throwable> failures;
    private final AtomicBoolean cancelled;

    private boolean waitingForCompletion;

    DefaultOperationQueue(ExecutorService executor, Action<? super T> worker) {
        this.executor =  MoreExecutors.listeningDecorator(executor);
        this.worker = worker;
        this.operations = Lists.newLinkedList();
        this.failures = Sets.newConcurrentHashSet();
        this.cancelled = new AtomicBoolean(false);
    }

    public void add(final T operation) {
        if (waitingForCompletion) {
            throw new IllegalStateException("OperationQueue cannot be reused once it has started completion.");
        }
        ListenableFuture<?> future = executor.submit(new Operation(operation));
        Futures.addCallback(future, new FailuresCallback());
        operations.add(future);
    }

    public void waitForCompletion() throws MultipleBuildOperationFailures {
        waitingForCompletion = true;

        CountDownLatch finished = new CountDownLatch(operations.size());
        for (ListenableFuture operation : operations) {
            Futures.addCallback(operation, new CompletionCallback(finished));
        }

        try {
            finished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // all operations are complete, check for errors
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(getFailureMessage(), failures);
        }
    }

    private String getFailureMessage() {
        if (failures.size()==1) {
            return "A build operation failed; see the error output for details.";
        }
        return "Multiple build operations failed; see the error output for details.";
    }

    private class FailuresCallback implements FutureCallback {
        public void onSuccess(Object result) {
            // don't care
        }

        public void onFailure(Throwable t) {
            if (!ignoredException(t)) {
                failures.add(t);
            }
            // TODO: Provide eager cancellation version of this too
            // For now, we'll keep continuing when we encounter a failure
            // cancel other operations ASAP
            // cancelled.set(true);
        }

        private boolean ignoredException(Throwable t) {
            return t instanceof CancellationException;
        }
    }

    private static class CompletionCallback implements FutureCallback {
        private final CountDownLatch finished;

        private CompletionCallback(CountDownLatch finished) {
            this.finished = finished;
        }

        public void onSuccess(Object result) {
            finished.countDown();
        }

        public void onFailure(Throwable t) {
            finished.countDown();
        }
    }

    class Operation implements Runnable {
        private final T operation;

        Operation(T operation) {
            this.operation = operation;
        }

        public void run() {
            if (!cancelled.get()) {
                worker.execute(operation);
            } else {
                throw new CancellationException("Cancelled by some other operation failure");
            }
        }

        public String toString() {
            return String.format("Worker %s for operation %s", worker, operation);
        }
    }
}
