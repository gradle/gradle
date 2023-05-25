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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemId;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.api.problems.interfaces.Solution;
import org.gradle.api.problems.internal.GradleExceptionWithContext;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationContextTracker;
import org.gradle.internal.problems.DefaultProblem;
import org.gradle.internal.problems.DefaultProblemLocation;
import org.gradle.internal.problems.DefaultSolution;

import java.util.Collections;

/**
 * Prototype Problems API.
 *
 * @since 8.3
 */
@Incubating
public class Problems {

    public static void reportWarning(ProblemId id, String message, String summary, String documentationUrl, String solution) {
        addProblem(new DefaultProblem(id, message, Severity.WARNING, null, summary, documentationUrl, "description", Collections.<Solution>singletonList(new DefaultSolution(documentationUrl, solution))));
    }

    public static void reportFailure(ProblemId id, String message, String file, Integer line, Throwable cause) {
        BuildOperationContext operationContext = BuildOperationContextTracker.peek();
        if (operationContext != null) {
            operationContext.addProblem(new DefaultProblem(id, message, Severity.ERROR, new DefaultProblemLocation(file, line), null, null, null, null));
            operationContext.failed(cause);
        }
        throw new GradleExceptionWithContext(cause);
    }

    private static void addProblem(Problem problem) {
        BuildOperationContext operationContext = BuildOperationContextTracker.peek();
        if (operationContext != null) {
            operationContext.addProblem(problem);
        }
    }
}
