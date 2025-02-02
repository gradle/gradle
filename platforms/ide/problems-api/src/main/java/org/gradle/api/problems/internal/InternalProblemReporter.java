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
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.internal.operations.OperationIdentifier;

public interface InternalProblemReporter extends ProblemReporter {

    /**
     * Reports the target problem with an explicit operation identifier.
     * <p>
     * This method is used to report problems from workers, where the operation identifier is not available.
     *
     * @param problem The problem to report.
     * @param id The operation identifier.
     */
    void report(Problem problem, OperationIdentifier id);

    InternalProblem internalCreate(Action<? super InternalProblemSpec> action);
}
