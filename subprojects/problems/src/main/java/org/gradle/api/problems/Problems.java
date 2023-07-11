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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;

import static java.util.Collections.singleton;

/**
 * Prototype Problems API.
 *
 * @since 8.4
 */
@Incubating
@ServiceScope(Scope.Global.class)
public abstract class Problems {

    private static Problems problemsService = new NoOpProblems();

    public static void init(Problems problemsService){
        Problems.problemsService = problemsService;
    }

    protected static ProblemBuilder create() {
        return problemsService.createProblemBuilder();
    }

    abstract public ProblemBuilder createProblemBuilder();

    protected static ProblemBuilder create(ProblemGroup problemGroup, String message, Severity severity, String type) {
        return problemsService.createProblemBuilder(problemGroup, message, severity, type);
    }
    abstract public ProblemBuilder createProblemBuilder(ProblemGroup problemGroup, String message, Severity severity, String type);

    protected static ProblemBuilder createError(ProblemGroup problemGroup, String message, String type) {
        return problemsService.createErrorProblemBuilder(problemGroup, message, type);
    }
    abstract public ProblemBuilder createErrorProblemBuilder(ProblemGroup problemGroup, String message, String type);

    protected static void collect(Throwable failure) {
        problemsService.collectError(failure);
    }
    abstract public void collectError(Throwable failure);

    protected static void collect(Problem problem) {
        problemsService.collectError(problem);
    }

    abstract public void collectError(Problem problem);

    abstract public void collectErrors(Collection<Problem> problem);

    protected static RuntimeException throwing(ProblemBuilder problem, RuntimeException cause) {
        problem.cause(cause);
        return throwing(singleton(problem.build()), cause);
    }
    protected static RuntimeException throwing(Collection<Problem> problems, RuntimeException cause) {
        collect(problems);
        throw cause;
    }

    protected static void collect(Collection<Problem> problems) {
            problemsService.collectErrors(problems);
    }

}
