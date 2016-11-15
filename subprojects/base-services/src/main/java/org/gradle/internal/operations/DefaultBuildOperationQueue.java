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
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.*;
import org.gradle.internal.UncheckedException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

class DefaultBuildOperationQueue<T extends BuildOperation> implements BuildOperationQueue<T> {
    private final BuildOperationWorkerRegistry.Operation owner;
    private final ListeningExecutorService executor;
    private final BuildOperationWorker<T> worker;

    private final List<QueuedOperation> operations;

    private String logLocation;

    private final AtomicBoolean waitingForCompletion = new AtomicBoolean();
    private final AtomicBoolean canceled = new AtomicBoolean();

    DefaultBuildOperationQueue(BuildOperationWorkerRegistry.Operation owner, ExecutorService executor, BuildOperationWorker<T> worker) {
        this.owner = owner;
        this.executor = MoreExecutors.listeningDecorator(executor);
        this.worker = worker;
        this.operations = Collections.synchronizedList(Lists.<QueuedOperation>newArrayList());
    }

    @Override
    public void add(final T operation) {
        if (waitingForCompletion.get()) {
            throw new IllegalStateException("BuildOperationQueue cannot be reused once it has started completion.");
        }
        OperationHolder operationHolder = new OperationHolder(owner, operation);
        ListenableFuture<?> future = executor.submit(operationHolder);
        operations.add(new QueuedOperation(operationHolder, future));
    }

    @Override
    public void cancel() {
        canceled.set(true);
        for (QueuedOperation operation : operations) {
            // Although we can cancel the future of a running operation, we have no way of knowing
            // that the operation was canceled after it began executing (i.e. isCanceled always returns
            // true) which is a problem because we need to know whether to wait on the result or not.
            // So we have to maintain the running state ourselves and only cancel operations we know
            // have not started executing.
            if (!operation.operationHolder.isStarted()) {
                operation.future.cancel(false);
            }
        }
    }

    public void waitForCompletion() throws MultipleBuildOperationFailures {
        waitingForCompletion.set(true);

        CountDownLatch finished = new CountDownLatch(operations.size());
        Queue<Throwable> failures = Queues.newConcurrentLinkedQueue();

        for (QueuedOperation operation : operations) {
            if (operation.future.isCancelled()) {
                // If it's canceled, we'll never get a callback, so we just remove it from
                // operations we're waiting for.
                finished.countDown();
            } else {
                Futures.addCallback(operation.future, new CompletionCallback(finished, failures));
            }
        }

        try {
            finished.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        // all operations are complete, check for errors
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(getFailureMessage(failures), failures, logLocation);
        }
    }

    @Override
    public void setLogLocation(String logLocation) {
        this.logLocation = logLocation;
    }

    private static String getFailureMessage(Collection<? extends Throwable> failures) {
        if (failures.size() == 1) {
            return "A build operation failed.";
        }
        return "Multiple build operations failed.";
    }

    private static class CompletionCallback implements FutureCallback {
        private final CountDownLatch finished;
        private final Collection<Throwable> failures;

        private CompletionCallback(CountDownLatch finished, Collection<Throwable> failures) {
            this.finished = finished;
            this.failures = failures;
        }

        @Override
        public void onSuccess(Object result) {
            finished.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
            failures.add(t);
            finished.countDown();
        }
    }

    private class QueuedOperation {
        final OperationHolder operationHolder;
        final ListenableFuture future;

        public QueuedOperation(OperationHolder operationHolder, ListenableFuture future) {
            this.operationHolder = operationHolder;
            this.future = future;
        }
    }

    private class OperationHolder implements Runnable {
        private final BuildOperationWorkerRegistry.Operation owner;
        private final T operation;
        private final AtomicBoolean started = new AtomicBoolean();

        OperationHolder(BuildOperationWorkerRegistry.Operation owner, T operation) {
            this.owner = owner;
            this.operation = operation;
        }

        @Override
        public void run() {
            // Don't execute if the queue has been canceled
            started.set(!canceled.get());
            if (started.get()) {
                runBuildOperation();
            }
        }

        private void runBuildOperation() {
            BuildOperationWorkerRegistry.Completion workerLease = owner.operationStart();
            try {
                worker.execute(operation);
            } finally {
                workerLease.operationFinish();
            }
        }

        public boolean isStarted() {
            return started.get();
        }

        @Override
        public String toString() {
            return "Worker ".concat(worker.getDisplayName()).concat(" for operation ").concat(operation.getDescription());
        }
    }
}
