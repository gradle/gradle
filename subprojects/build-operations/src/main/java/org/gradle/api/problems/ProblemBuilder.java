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
import org.gradle.api.problems.internal.GradleExceptionWithProblem;
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder;
import org.gradle.internal.problems.DefaultProblem;
import org.gradle.internal.problems.DefaultProblemLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for problems.
 *
 * @since 8.3
 */
@Incubating
public class ProblemBuilder {
    private final ProblemId problemId;
    private final String message;
    private final Severity severity;
    private String path;
    private Integer line;
    private String description;
    private String documentationUrl;
    private List<String> solution;
    private Throwable cause;

    public ProblemBuilder(ProblemId problemId, String message, Severity severity) {
        this.problemId = problemId;
        this.message = message;
        this.severity = severity;
    }

    public ProblemBuilder location(String path, Integer line) {
        this.path = path;
        this.line = line;
        return this;
    }

    public ProblemBuilder description(String description) {
        this.description = description;
        return this;
    }

    public ProblemBuilder documentedAt(@Nullable String documentationUrl) {
        this.documentationUrl = documentationUrl;
        return this;
    }

    public ProblemBuilder solution(@Nullable String solution) {
        if (this.solution == null) {
            this.solution = new ArrayList<String>();
        }
        this.solution.add(solution);
        return this;
    }

    public ProblemBuilder cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    private Problem build() {
        return new DefaultProblem(
            problemId,
            message,
            severity,
            path == null ? null : new DefaultProblemLocation(path, line),
            documentationUrl,
            description,
            solution,
            cause
        );
    }

    public ProblemThrower report() {
        Problem problem = build();
        ProblemsProgressEventEmitterHolder.get().emitNowIfCurrent(problem);
        return new ProblemThrower(problem);
    }

    private static void throwPossibleError(Problem problem) {
        if (problem.getSeverity() == Severity.ERROR) {
            Throwable t = problem.getCause();
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new GradleExceptionWithProblem(problem);
        }
    }


    /**
     * allows to throw problems in a builder like fashion
     *
     * @since 8.3
     */
    @Incubating
    public static class ProblemThrower {
        private final Problem problem;

        public ProblemThrower(Problem problem) {
            this.problem = problem;
        }

        public void throwIt(){
            throwPossibleError(problem);
        }
    }
}
