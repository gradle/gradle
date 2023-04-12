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

import com.google.common.collect.ImmutableList;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.history.AfterExecutionState;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;

public class UpToDateResult extends AfterExecutionResult {
    private final ImmutableList<String> executionReasons;
    private final OriginMetadata reusedOutputOriginMetadata;

    public UpToDateResult(AfterExecutionResult parent, ImmutableList<String> executionReasons, @Nullable OriginMetadata reusedOutputOriginMetadata) {
        super(parent);
        this.executionReasons = executionReasons;
        this.reusedOutputOriginMetadata = reusedOutputOriginMetadata;
    }

    public UpToDateResult(Duration duration, Try<ExecutionEngine.Execution> execution, @Nullable AfterExecutionState afterExecutionState, ImmutableList<String> executionReasons, @Nullable OriginMetadata reusedOutputOriginMetadata) {
        super(duration, execution, afterExecutionState);
        this.executionReasons = executionReasons;
        this.reusedOutputOriginMetadata = reusedOutputOriginMetadata;
    }

    protected UpToDateResult(UpToDateResult parent) {
        this(parent, parent.getExecutionReasons(), parent.getReusedOutputOriginMetadata().orElse(null));
    }

    /**
     * A list of messages describing the first few reasons encountered that caused the work to be executed.
     * An empty list means the work was up-to-date and hasn't been executed.
     */
    public ImmutableList<String> getExecutionReasons() {
        return executionReasons;
    }

    /**
     * If a previously produced output was reused in some way, the reused output's origin metadata is returned.
     */
    public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
        return Optional.ofNullable(reusedOutputOriginMetadata);
    }
}
