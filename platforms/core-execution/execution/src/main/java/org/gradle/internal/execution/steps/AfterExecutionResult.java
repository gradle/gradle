/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.Try;
import org.gradle.internal.execution.Execution;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AfterExecutionResult extends Result {
    private final CompletableFuture<Optional<ExecutionOutputState>> futureOutputState;

    public AfterExecutionResult(Duration duration, Try<Execution> execution, @Nullable ExecutionOutputState afterExecutionOutputState) {
        super(duration, execution);
        this.futureOutputState = CompletableFuture.completedFuture(Optional.ofNullable(afterExecutionOutputState));
    }

    public AfterExecutionResult(Result parent, @Nullable ExecutionOutputState afterExecutionOutputState) {
        super(parent);
        this.futureOutputState = CompletableFuture.completedFuture(Optional.ofNullable(afterExecutionOutputState));
    }

    public AfterExecutionResult(Result parent, CompletableFuture<Optional<ExecutionOutputState>> futureOutputState) {
        super(parent);
        this.futureOutputState = futureOutputState;
    }

    protected AfterExecutionResult(AfterExecutionResult parent) {
        super(parent);
        this.futureOutputState = parent.getAfterExecutionOutputStateFuture();
    }

    /**
     * State after execution, or {@link Optional#empty()} if work is untracked.
     * Blocks until the state is available (snapshotting may happen asynchronously).
     */
    @VisibleForTesting
    public Optional<ExecutionOutputState> getAfterExecutionOutputState() {
        return futureOutputState.join();
    }

    /**
     * Future for the state after execution. Use for non-blocking chaining of post-execution work.
     * The future completes with {@link Optional#empty()} if the work is untracked.
     */
    public CompletableFuture<Optional<ExecutionOutputState>> getAfterExecutionOutputStateFuture() {
        return futureOutputState;
    }
}
