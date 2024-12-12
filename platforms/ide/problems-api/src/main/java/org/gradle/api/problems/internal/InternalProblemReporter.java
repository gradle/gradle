/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.internal.operations.OperationIdentifier;

import java.util.Collection;

public interface InternalProblemReporter extends ProblemReporter {

    /**
     * Creates a new problem without reporting it immediately.
     * The created problem can be later reported with {@link #report(Problem)}.
     *
     * @param action The problem configuration.
     * @return The new problem.
     */
    Problem create(Action<InternalProblemSpec> action);

    /**
     * Reports the target problem.
     *
     * @param problem The problem to report.
     */
    void report(Problem problem);

    /**
     * Reports the target problems.
     *
     * @param problems The problems to report.
     */
    void report(Collection<? extends Problem> problems);

    /**
     * Reports the target problem with an explicit operation identifier.
     *
     * @param problem The problem to report.
     * @param id The operation identifier.
     */
    void report(Problem problem, OperationIdentifier id);

    /**
     * Reports the target problems and throws a runtime exception. When this method is used, all reported problems will be associated with the thrown exception.
     *
     * @param exception the exception to throw after reporting the problems
     * @param problems the problems to report
     * @return nothing, the method throws an exception
     * @since 8.12
     */
    RuntimeException throwing(Throwable exception, Collection<? extends Problem> problems);

    /**
     * Creates a new problem builder for other, more specific builders to use.
     * @return a new problem builder
     */
    InternalProblemBuilder createProblemBuilder();
}
