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

import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.UndocumentedProblemBuilder;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;

import java.util.Collection;

import static org.gradle.api.problems.interfaces.ProblemGroup.GENERIC;
import static org.gradle.api.problems.interfaces.Severity.ERROR;

public class DefaultProblems extends Problems {
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;

    public DefaultProblems(BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
    }

    public UndocumentedProblemBuilder createProblemBuilder() {
        return new DefaultProblemBuilder(buildOperationProgressEventEmitter);
    }


    public void collectError(Throwable failure) {
        new DefaultProblemBuilder(buildOperationProgressEventEmitter)
            .undocumented()
            .noLocation()
            .severity(ERROR)
            .message(failure.getMessage())
            .type("generic_exception")
            .group(GENERIC)
            .cause(failure)
            .report();
    }

    @Override
    public void collectError(Problem problem) {
        buildOperationProgressEventEmitter.emitNowIfCurrent(problem);
//        ProblemsProgressEventEmitterHolder.get().emitNowIfCurrent(problem);
    }

    @Override
    public void collectErrors(Collection<Problem> problem) {
        problem.forEach(this::collectError);
    }
}
