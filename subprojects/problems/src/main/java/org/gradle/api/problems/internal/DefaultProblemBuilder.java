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

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.problems.interfaces.ProblemBuilderWithoutMessage;
import org.gradle.api.problems.interfaces.ProblemBuilderWithoutSeverity;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.api.problems.interfaces.UnTypedProblemBuilder;
import org.gradle.api.problems.interfaces.UndocumentedProblemBuilder;
import org.gradle.api.problems.interfaces.UngroupedProblemBuilder;
import org.gradle.api.problems.interfaces.UnlocatedProblemBuilder;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for problems.
 *
 * @since 8.3
 */
@Incubating
public class DefaultProblemBuilder implements ProblemBuilder,
    UndocumentedProblemBuilder,
    UnlocatedProblemBuilder,
    UngroupedProblemBuilder,
    ProblemBuilderWithoutMessage,
    ProblemBuilderWithoutSeverity,
    UnTypedProblemBuilder {

    private ProblemGroup problemGroup;
    private String message;
    private String problemType;
    private final Problems problemsService;
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;
    private Severity severity;
    private String path;
    private Integer line;
    private Integer column;
    private boolean noLocation = false;
    private String description;
    private DocLink documentationUrl;
    private boolean explicitlyUndocumented = false;
    private List<String> solution;
    private Throwable cause;
    protected final Map<String, String> additionalMetadata = new HashMap<>();

    public DefaultProblemBuilder(Problems problemsService, BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
        this.problemsService = problemsService;
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
    }

    @Override
    public ProblemBuilder group(ProblemGroup group) {
        this.problemGroup = group;
        return this;
    }

    @Override
    public ProblemBuilder group(String group) {
        if (problemsService != null) {

            ProblemGroup existingGroup = problemsService.getProblemGroup(group);
            if (existingGroup == null) {
                throw new GradleException("Problem group " + group + " does not exist, either use existing group or register a new one");
            }
            group(existingGroup);
        }
        return this;
    }

    @Override
    public UnTypedProblemBuilder message(String message) {
        this.message = message;
        return this;
    }

    @Override
    public ProblemBuilderWithoutMessage severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    public ProblemBuilderWithoutSeverity location(String path, Integer line) {
        this.path = path;
        this.line = line;
        return this;
    }

    public ProblemBuilderWithoutSeverity location(String path, Integer line, Integer column) {
        this.path = path;
        this.line = line;
        this.column = column;
        return this;
    }

    @Override
    public ProblemBuilderWithoutSeverity noLocation() {
        this.noLocation = true;
        return this;
    }

    public ProblemBuilder description(String description) {
        this.description = description;
        return this;
    }

    public UnlocatedProblemBuilder documentedAt(DocLink doc) {
        this.documentationUrl = doc;
        return this;
    }

    @Override
    public UnlocatedProblemBuilder undocumented() {
        this.explicitlyUndocumented = true;
        return this;
    }

    public UngroupedProblemBuilder type(String problemType) {
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

    public ProblemBuilder withMetadata(String key, String value) {
        this.additionalMetadata.put(key, value);
        return this;
    }

    public Problem build() {
        if (!explicitlyUndocumented && documentationUrl == null) {
            throw new IllegalStateException("Problem is not documented: " + message);
        }

        if (!noLocation) {
            if (path == null) {
                throw new IllegalStateException("Problem location path is not set: " + message);
            }
            if (line == null) {
                throw new IllegalStateException("Problem location line is not set: " + message);
            }
            // Column is optional field, so we don't need to check it
        }

        return new DefaultProblem(
            problemGroup,
            message,
            severity,
            path == null ? null : new DefaultProblemLocation(path, line, column),
            documentationUrl,
            description,
            solution,
            cause,
            problemType,
            additionalMetadata);
    }

    public void report() {
        Problem problem = build();
        report(problem);
    }

    public RuntimeException throwIt() {
        throw throwError(build());
    }

    public RuntimeException throwError(Problem problem) {
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

    private void report(Problem problem) {
        buildOperationProgressEventEmitter.emitNowIfCurrent(problem);
    }
}
