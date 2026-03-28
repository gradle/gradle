/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public class DefaultPostExecutionWorkQueue implements PostExecutionWorkQueue, Stoppable {

    private final ManagedExecutor executor;

    public DefaultPostExecutionWorkQueue(ManagedExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void submit(Runnable work) {
        executor.execute(work);
    }

    @Override
    public <T> CompletableFuture<T> submitAsync(Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(callable.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public void stop() {
        executor.stop();
    }
}
