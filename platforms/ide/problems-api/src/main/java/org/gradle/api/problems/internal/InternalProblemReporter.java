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

public interface InternalProblemReporter extends ProblemReporter {

    /**
     * Creates a new problem without reporting it immediately.
     * The created problem can be later reported with {@link #report(ProblemReport)}.
     *
     * @param action The problem configuration.
     * @return The new problem.
     */
    ProblemReport create(Action<InternalProblemSpec> action);

    /**
     * Reports the target problem.
     *
     * @param problem The problem to report.
     */
    void report(ProblemReport problem);

    /**
     * Reports the target problem with an explicit operation identifier.
     *
     * @param problem The problem to report.
     * @param id      The operation identifier.
     */
    void report(ProblemReport problem, OperationIdentifier id);
}
