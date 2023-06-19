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
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.api.problems.internal.DefaultProblemBuilder;
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder;

import java.util.Collection;

import static java.util.Collections.singleton;
import static org.gradle.api.problems.interfaces.ProblemGroup.GENERIC;
import static org.gradle.api.problems.interfaces.Severity.ERROR;

/**
 * Prototype Problems API.
 *
 * @since 8.3
 */
@Incubating
public class Problems {

    public static ProblemBuilder create(ProblemGroup problemGroup, String message, Severity severity, String type) {
        return new DefaultProblemBuilder(problemGroup, message, severity, type);
    }

    public static ProblemBuilder createError(ProblemGroup problemGroup, String message, String type) {
        return new DefaultProblemBuilder(problemGroup, message, ERROR, type);
    }

    public static void collect(Throwable failure) {
        new DefaultProblemBuilder(GENERIC, failure.getMessage(), ERROR, "generic_exception")
            .cause(failure)
            .report();
    }

    public static void collect(Problem problem) {
        ProblemsProgressEventEmitterHolder.get().emitNowIfCurrent(problem);
    }

    public static RuntimeException throwing(ProblemBuilder problem, RuntimeException cause) {
        problem.cause(cause);
        return throwing(singleton(problem.build()), cause);
    }
    public static RuntimeException throwing(Collection<Problem> problems, RuntimeException cause) {
        collect(problems);
        throw cause;
    }

    public static void collect(Collection<Problem> problems) {
        for (Problem problem : problems){
            ProblemsProgressEventEmitterHolder.get().emitNowIfCurrent(problem);
        }
    }
}
