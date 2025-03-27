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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.ProblemLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface InternalProblem extends Problem {

    /**
     * Returns a problem builder with fields initialized with values from this instance.
     */
    InternalProblemBuilder toBuilder(ProblemsInfrastructure infrastructure);

    /**
     * Returns the problem definition, i.e. the data that is independent of the report context.
     */
    ProblemDefinition getDefinition();

    /**
     * Declares a short, but context-dependent message for this problem.
     *
     */
    @Nullable
    String getContextualLabel();

    /**
     * Returns solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     */
    List<String> getSolutions();

    /**
     * A long description detailing the problem.
     * <p>
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use {@link #getSolutions()}.
     */
    @Nullable
    String getDetails();

    /**
     * Returns the locations where the problem originated.
     * <p>
     * Might be empty if the origin is not known.
     */
    List<ProblemLocation> getOriginLocations();

    /**
     * Returns additional locations, which can help to understand the problem further.
     * <p>
     * For example, if a problem was emitted during task execution, the task path will be available in this list.
     * <p>
     * Might be empty if there is no meaningful contextual information.
     */
    List<ProblemLocation> getContextualLocations();

    /**
     * The exception that caused the problem.
     */
    @Nullable
    Throwable getException();

    /**
     * Additional data attached to the problem.
     * <p>
     * The supported types are listed on {@link AdditionalData}.
     */
    @Nullable
    AdditionalData getAdditionalData();
}
