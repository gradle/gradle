/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionOutputState;

import javax.annotation.Nullable;
import java.time.Duration;

public class CachingResult extends UpToDateResult {

    private final CachingState cachingState;

    public CachingResult(UpToDateResult parent, CachingState cachingState) {
        super(parent);
        this.cachingState = cachingState;
    }

    public CachingResult(Duration duration, Try<Execution> execution, @Nullable ExecutionOutputState afterExecutionOutputState, ImmutableList<String> executionReasons, @Nullable OriginMetadata reusedOutputOriginMetadata, CachingState cachingState) {
        super(duration, execution, afterExecutionOutputState, executionReasons, reusedOutputOriginMetadata);
        this.cachingState = cachingState;
    }

    protected CachingResult(CachingResult parent) {
        this(parent, parent.getCachingState());
    }

    public static CachingResult shortcutResult(Duration duration, Execution execution, @Nullable ExecutionOutputState afterExecutionOutputState, @Nullable String executionReason, @Nullable OriginMetadata reusedOutputOriginMetadata) {
        return new CachingResult(duration, Try.successful(execution), afterExecutionOutputState, executionReason == null ? ImmutableList.of() : ImmutableList.of(executionReason), reusedOutputOriginMetadata, CachingState.NOT_DETERMINED);
    }

    public CachingState getCachingState() {
        return cachingState;
    }
}
