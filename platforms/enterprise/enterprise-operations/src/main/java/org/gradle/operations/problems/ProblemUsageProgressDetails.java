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

package org.gradle.operations.problems;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A problem from the problem API reported via a progress event.
 *
 * @since 8.14
 */
public interface ProblemUsageProgressDetails {

    /**
     * The problem definition, i.e. the data that is independent of the usage.
     *
     * @since 8.14
     */
    ProblemDefinition getDefinition();

    /**
     * The severity of the problem.
     *
     * @since 8.14
     */
    ProblemSeverity getSeverity();

    /**
     * Declares a short, but usage-dependent message for this problem.
     *
     * @return the contextual label, or {@code null} if not specified. Then the display name from the definition should be used.
     * @since 8.14
     */
    @Nullable
    String getContextualLabel();

    /**
     * Returns solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     *
     * @since 8.14
     */
    List<String> getSolutions();

    /**
     * A long description detailing the problem.
     * <p>
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use {@link #getSolutions()}.
     * <p>
     * {@code null} if no details have been specified.
     *
     * @since 8.14
     */
    @Nullable
    String getDetails();

    /**
     * Returns the locations where the problem originated.
     * <p>
     * Might be empty if the origin is not known.
     *
     * @since 8.14
     */
    List<ProblemLocation> getOriginLocations();

    /**
     * Returns additional locations, which can help to understand the problem further.
     * <p>
     * For example, if a problem was emitted during task execution, the task path will be available in this list.
     * <p>
     * Might be empty if there is no meaningful contextual information.
     *
     * @since 8.14
     */
    List<ProblemLocation> getContextualLocations();

    /**
     * The failure associated with the problem.
     * <p>
     * {@code null} if the problem is not associated with a failure.
     *
     * @since 8.14
     */
    @Nullable
    Throwable getFailure();

}
