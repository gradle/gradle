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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.internal.concurrent.StoppableExecutor;

/**
 *
 */
public class DefaultOperationQueue<T> implements OperationQueue<T> {
    final StoppableExecutor executor;
    final Action<? super T> worker;

    public DefaultOperationQueue(StoppableExecutor executor, Action<? super T> worker) {
        this.executor = executor;
        this.worker = worker;
    }

    public void add(final T operation) {
        executor.execute(new Runnable() {
            public void run() {
                worker.execute(operation);
            }

            public String toString() {
                return String.format("Worker %s for operation %s", worker, operation);
            }
        });
    }

    public void waitForCompletion() throws GradleException {
        executor.stop();
    }
}
