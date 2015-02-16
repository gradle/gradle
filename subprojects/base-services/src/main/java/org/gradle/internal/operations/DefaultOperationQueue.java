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
import com.google.common.util.concurrent.*;
import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

class DefaultOperationQueue<T> implements OperationQueue<T> {
    private final ListeningExecutorService executor;
    private final Action<? super T> worker;

    private final List<ListenableFuture> operations;

    private boolean waitingForCompletion;

    DefaultOperationQueue(ExecutorService executor, Action<? super T> worker) {
        this.executor =  MoreExecutors.listeningDecorator(executor);
        this.worker = worker;
        this.operations = Lists.newLinkedList();
    }

    public void add(final T operation) {
        if (waitingForCompletion) {
            throw new IllegalStateException("OperationQueue cannot be reused once it has started completion.");
        }
        ListenableFuture<?> future = executor.submit(new Operation(operation));
        operations.add(future);
    }

    public void waitForCompletion() throws MultipleBuildOperationFailures {
        waitingForCompletion = true;

        CountDownLatch finished = new CountDownLatch(operations.size());
        List<Throwable> failures = Lists.newCopyOnWriteArrayList();

        for (ListenableFuture operation : operations) {
            Futures.addCallback(operation, new CompletionCallback(finished, failures));
        }

        try {
            finished.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        // all operations are complete, check for errors
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(getFailureMessage(failures), failures);
        }
    }

    private String getFailureMessage(List<Throwable> failures) {
        if (failures.size()==1) {
            return "A build operation failed.";
        }
        return "Multiple build operations failed.";
    }

    private static class CompletionCallback implements FutureCallback {
        private final CountDownLatch finished;
        private final List<Throwable> failures;

        private CompletionCallback(CountDownLatch finished, List<Throwable> failures) {
            this.finished = finished;
            this.failures = failures;
        }

        public void onSuccess(Object result) {
            finished.countDown();
        }

        public void onFailure(Throwable t) {
            finished.countDown();
            failures.add(t);
        }
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
