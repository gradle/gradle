/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.problems.failure;

import java.util.List;

/**
 * Content of a thrown exception with classified stack frames.
 * <p>
 * Failures can have multiple causes via the {@link org.gradle.internal.exceptions.MultiCauseException}.
 * <p>
 * Failures are guaranteed to not have circular references.
 *
 * @see FailureFactory
 */
public interface Failure {

    Class<? extends Throwable> getExceptionType();

    /**
     * A failure summary usually containing the type of the original exception and its message.
     *
     * @see Throwable#toString()
     */
    String getHeader();

    /**
     * Stack frames from the original exception.
     */
    List<StackTraceElement> getStackTrace();

    /**
     * Relevance of a given stack frame in the {@link #getStackTrace() stack trace}.
     */
    StackTraceRelevance getStackTraceRelevance(int frameIndex);

    /**
     * Failures suppressed in the original exception.
     */
    List<Failure> getSuppressed();

    /**
     * List of causes for this failure.
     * <p>
     * There could be more than one cause if the failure was derived from a {@link org.gradle.internal.exceptions.MultiCauseException}.
     */
    List<Failure> getCauses();

    /**
     * Returns the index of the first matching frame in the stack trace, or {@code -1} if not found.
     */
    int indexOfStackFrame(int start, StackFramePredicate predicate);

}
