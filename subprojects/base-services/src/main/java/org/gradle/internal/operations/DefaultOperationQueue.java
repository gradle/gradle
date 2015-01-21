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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class DefaultOperationQueue<T> implements OperationQueue<T> {
    private final ListeningExecutorService executor;
    private final Action<? super T> worker;
    private final List<ListenableFuture<?>> workFutures;

    private boolean completed;

    public DefaultOperationQueue(ListeningExecutorService executor, Action<? super T> worker) {
        this.executor = executor;
        this.worker = worker;
        this.workFutures = Lists.newLinkedList();
    }

    public void add(final T operation) {
        if (completed) {
            throw new IllegalStateException("OperationQueues cannot be reused once they have started completion");
        }
        workFutures.add(executor.submit(new Operation(operation)));
    }

    public void waitForCompletion() throws GradleException {
        completed = true;

        try {
            // wait for all tasks to complete
            Futures.allAsList(workFutures).get();
        } catch (InterruptedException e) {
            throw new UncheckedException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GradleException) {
                // propagate GradleException from underlying worker
                throw (GradleException)e.getCause();
            }
            throw new GradleException(String.format("Build operation for worker %s failed", worker), e.getCause());
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
