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
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.UnitOfWork;

import java.time.Duration;

public interface Result {

    /**
     * The elapsed wall clock time of the corresponding {@link UnitOfWork#execute(UnitOfWork.ExecutionRequest)} invocation.
     *
     * The meaning of value is undefined for results that do not represent an attempt to perform the work.
     * For example, result specializations such as {@link CachingResult} that represent a result where the work
     * as avoided do not have a meaningful value.
     *
     * For results representing an attempt to perform the work, the duration is available for successful and failed attempts.
     */
    Duration getDuration();

    Try<ExecutionResult> getExecutionResult();
}
