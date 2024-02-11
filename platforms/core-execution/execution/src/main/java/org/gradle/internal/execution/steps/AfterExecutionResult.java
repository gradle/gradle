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

import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.history.ExecutionOutputState;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;

public class AfterExecutionResult extends Result {
    private final ExecutionOutputState afterExecutionOutputState;

    public AfterExecutionResult(Duration duration, Try<ExecutionEngine.Execution> execution, @Nullable ExecutionOutputState afterExecutionOutputState) {
        super(duration, execution);
        this.afterExecutionOutputState = afterExecutionOutputState;
    }

    public AfterExecutionResult(Result parent, @Nullable ExecutionOutputState afterExecutionOutputState) {
        super(parent);
        this.afterExecutionOutputState = afterExecutionOutputState;
    }

    protected AfterExecutionResult(AfterExecutionResult parent) {
        this(parent, parent.getAfterExecutionOutputState().orElse(null));
    }

    /**
     * State after execution, or {@link Optional#empty()} if work is untracked.
     */
    public Optional<ExecutionOutputState> getAfterExecutionOutputState() {
        return Optional.ofNullable(afterExecutionOutputState);
    }
}
