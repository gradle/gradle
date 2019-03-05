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

package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableList;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;

import java.util.List;
import java.util.Optional;

public interface TaskExecuterResult {
    /**
     * Returns the reasons for executing this task. An empty list means the task was not executed.
     */
    List<String> getExecutionReasons();

    /**
     * The outcome of executing the task.
     */
    Try<ExecutionOutcome> getOutcome();

    /**
     * If the execution resulted in some previous output being reused, this returns its origin metadata.
     */
    Optional<OriginMetadata> getReusedOutputOriginMetadata();

    TaskExecuterResult NO_REUSED_OUTPUT = new TaskExecuterResult() {
        @Override
        public List<String> getExecutionReasons() {
            return ImmutableList.of();
        }

        @Override
        public Try<ExecutionOutcome> getOutcome() {
            return Try.successful(ExecutionOutcome.EXECUTED_FULLY);
        }

        @Override
        public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
            return Optional.empty();
        }
    };
}
