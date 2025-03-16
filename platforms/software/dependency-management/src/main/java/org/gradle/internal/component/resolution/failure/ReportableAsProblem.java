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

package org.gradle.internal.component.resolution.failure;

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.InternalProblems;

/**
 * Represents a {@link Throwable} that can be reported as a {@link Problem} to
 * the {@link org.gradle.api.problems.Problems} service.
 * <p>
 * This is necessary, as Dependency Management uses exceptions for flow control, and some exceptions are meant to
 * be "ignored", in the sense that they don't fail a build and represent dependency failures that can be recovered from.  Due
 * to this usage, we can't follow the typical advice of creating and reporting a problem at the time we create and
 * throw the exception.  Instead, exceptions that implement this interface understand how to report themselves as
 * problems as some later time, when the Dependency Resolution machinery can be confident that the exception represents
 * a failure that should be reported to the user.
 */
public interface ReportableAsProblem {
    /**
     * Reports this exception as a problem to the given {@link InternalProblems} service.
     * <p>
     * This method returns the exception itself, so that it can be thrown immediately after the problem is reported.
     *
     * @param problemsService the problems service to report the problem to
     * @return the exception to report (should be {@code this})
     */
    @SuppressWarnings("UnusedReturnValue")
    Throwable reportAsProblem(InternalProblems problemsService);
}
