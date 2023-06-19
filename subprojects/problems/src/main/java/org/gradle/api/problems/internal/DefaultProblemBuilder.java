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

import org.gradle.api.Incubating;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.problems.interfaces.ProblemLocation;
import org.gradle.api.problems.interfaces.Severity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for problems.
 *
 * @since 8.3
 */
@Incubating
public class DefaultProblemBuilder implements ProblemBuilder {

    private final ProblemGroup problemGroup;
    private String message;
    private String problemType;
    private final Severity severity;
    private String path;
    private Integer line;
    private boolean noLocation = false;

    private String description;
    private DocLink documentationUrl;
    private boolean explicitlyUndocumented = false;
    private List<String> solution;
    private Throwable cause;

    public DefaultProblemBuilder(ProblemGroup problemGroup, String message, Severity severity, String type) { //add type
        this.problemGroup = problemGroup;
        this.message = message;
        this.severity = severity;
        this.problemType = type;
    }

    public DefaultProblemBuilder(ProblemGroup problemGroup, Throwable cause, Severity severity) {
        this.problemGroup = problemGroup;
        this.cause = cause;
        this.severity = severity;
    }

    public DefaultProblemBuilder(Problem problem) {
        this.problemGroup = problem.getProblemGroup();
        this.severity = problem.getSeverity();
        this.message = problem.getMessage();
        ProblemLocation where = problem.getWhere();
        if (where != null) {
            this.path = where.getPath();
            this.line = where.getLine();
        }
        this.documentationUrl = problem.getDocumentationLink();
        for (String solution : problem.getSolutions()) {
            this.solution(solution);
        }
    }

    //add noLocation
    public ProblemBuilder location(String path, Integer line) {
        this.path = path;
        this.line = line;
        return this;
    }

    @Override
    public ProblemBuilder noLocation() {
        this.noLocation = true;
        return this;
    }

    public ProblemBuilder description(String description) {
        this.description = description;
        return this;
    }

    // add "undocumented"
    public ProblemBuilder documentedAt(String page, String section) {
        this.documentationUrl = new DefaultDocLink(page, section);
        return this;
    }

    @Override
    public ProblemBuilder undocumented() {
        this.explicitlyUndocumented = true;
        return this;
    }

    public ProblemBuilder type(String problemType) {
        this.problemType = problemType;
        return this;
    }

    public ProblemBuilder solution(@Nullable String solution) {
        if (this.solution == null) {
            this.solution = new ArrayList<>();
        }
        this.solution.add(solution);
        return this;
    }

    public ProblemBuilder cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public Problem build() {
        if (!explicitlyUndocumented && documentationUrl == null) {
            throw new IllegalStateException("Problem is not documented: " + message);
        }

        if(!noLocation && (path == null || line == null)) {
            throw new IllegalStateException("Problem has no location: " + message);
        }

        return new DefaultProblem(
            problemGroup,
            message,
            severity,
            path == null ? null : new DefaultProblemLocation(path, line),
            documentationUrl,
            description,
            solution,
            cause,
            problemType);
    }

    public void report() {
        Problem problem = build();
        report(problem);
    }

    public RuntimeException throwIt() {
        throw throwError(build());
    }

    protected static void throwPossibleError(Problem problem) {
        if (problem.getSeverity() == Severity.ERROR) {
            throw throwError(problem);
        }
    }

    public static RuntimeException throwError(Problem problem) {
        Throwable t = problem.getCause();
        if (t instanceof InterruptedException) {
            report(problem);
            Thread.currentThread().interrupt();
        }
        if (t instanceof RuntimeException) {
            report(problem);
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            report(problem);
            throw (Error) t;
        }
        throw new GradleExceptionWithProblem(problem);
    }

    private static void report(Problem problem) {
        ProblemsProgressEventEmitterHolder.get().emitNowIfCurrent(problem);
    }
}
