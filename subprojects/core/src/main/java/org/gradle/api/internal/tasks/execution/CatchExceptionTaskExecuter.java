/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.caching.FailureWithCachingState;

import java.util.List;
import java.util.Optional;

public class CatchExceptionTaskExecuter implements TaskExecuter {
    private final TaskExecuter delegate;

    public CatchExceptionTaskExecuter(TaskExecuter delegate) {
        this.delegate = delegate;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        try {
            return delegate.execute(task, state, context);
        } catch (FailureWithCachingState failureWithCachingState) {
            state.setOutcome(new TaskExecutionException(task, failureWithCachingState));
            return new CachingStateTaskExecuterResult(failureWithCachingState);
        } catch (RuntimeException e) {
            state.setOutcome(new TaskExecutionException(task, e));
            return TaskExecuterResult.WITHOUT_OUTPUTS;
        }
    }

    private static class CachingStateTaskExecuterResult implements TaskExecuterResult {
        private final FailureWithCachingState failureWithCachingState;

        public CachingStateTaskExecuterResult(FailureWithCachingState failureWithCachingState) {
            this.failureWithCachingState = failureWithCachingState;
        }

        @Override
        public List<String> getExecutionReasons() {
            return ImmutableList.of();
        }

        @Override
        public boolean executedIncrementally() {
            return false;
        }

        @Override
        public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
            return Optional.empty();
        }

        @Override
        public CachingState getCachingState() {
            return failureWithCachingState.getCachingState();
        }
    }
}
