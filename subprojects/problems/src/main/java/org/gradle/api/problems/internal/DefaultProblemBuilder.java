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
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningDocumentation;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningGroup;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningLocation;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningLabel;
import org.gradle.api.problems.interfaces.ProblemBuilderDefiningType;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.problems.interfaces.ProblemLocation;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.api.problems.interfaces.Severity.ERROR;

/**
 * Builder for problems.
 *
 * @since 8.3
 */
@Incubating
public class DefaultProblemBuilder implements ProblemBuilder,
    ProblemBuilderDefiningDocumentation,
    ProblemBuilderDefiningLocation,
    ProblemBuilderDefiningGroup,
    ProblemBuilderDefiningLabel,
    ProblemBuilderDefiningType {

    private ProblemGroup problemGroup;
    private String label;
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
    private RuntimeException exception;
    protected final Map<String, String> additionalMetadata = new HashMap<>();

    public DefaultProblemBuilder(@Nullable Problems problemsService, BuildOperationProgressEventEmitter buildOperationProgressEventEmitter) {
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
    public ProblemBuilderDefiningDocumentation label(String label, Object... args) {
        this.label = String.format(label, args);
        return this;
    }

    @Override
    public ProblemBuilder severity(Severity severity) {
        this.severity = severity;
        return this;
    }

    public ProblemBuilderDefiningType location(String path, Integer line) {
        this.path = path;
        this.line = line;
        return this;
    }

    public ProblemBuilderDefiningType location(String path, Integer line, Integer column) {
        this.path = path;
        this.line = line;
        this.column = column;
        return this;
    }

    @Override
    public ProblemBuilderDefiningType noLocation() {
        this.noLocation = true;
        return this;
    }

    public ProblemBuilder details(String details) {
        this.description = details;
        return this;
    }

    public ProblemBuilderDefiningLocation documentedAt(DocLink doc) {
        this.documentationUrl = doc;
        return this;
    }

    @Override
    public ProblemBuilderDefiningLocation undocumented() {
        this.explicitlyUndocumented = true;
        return this;
    }

    public ProblemBuilderDefiningGroup type(String problemType) {
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

    public ProblemBuilder additionalData(String key, String value) {
        this.additionalMetadata.put(key, value);
        return this;
    }

    @Override
    public ProblemBuilder withException(RuntimeException e) {
        this.exception = e;
        return this;
    }

    public Problem build() {
        return buildInternal(null);
    }

    @Nonnull
    private DefaultProblem buildInternal(@Nullable Severity severity) {
        if (!explicitlyUndocumented && documentationUrl == null) {
            throw new IllegalStateException("Problem is not documented: " + label);
        }

        if (!noLocation) {
            if (path == null) {
                throw new IllegalStateException("Problem location path is not set: " + label);
            }
            if (line == null) {
                throw new IllegalStateException("Problem location line is not set: " + label);
            }
            // Column is optional field, so we don't need to check it
        }

        return new DefaultProblem(
            problemGroup,
            label,
            getSeverity(severity),
            getProblemLocation(),
            documentationUrl,
            description,
            solution,
            exception,
            problemType,
            additionalMetadata);
    }


    @Nullable
    private ProblemLocation getProblemLocation() {
        return path == null ? null : new DefaultProblemLocation(path, line, column);
    }

    @Nonnull
    private Severity getSeverity(@Nullable Severity severity) {
        if (severity != null) {
            return severity;
        }
        return getSeverity();
    }

    private Severity getSeverity() {
        if (this.severity == null) {
            return Severity.WARNING;
        }
        return this.severity;
    }

    public void report() {
        Problem problem = build();
        report(problem);
    }

    public RuntimeException throwIt() {
        throw throwError(exception, buildInternal(ERROR));
    }

    private RuntimeException throwError(RuntimeException exception, DefaultProblem problem) {
        throw throwError(exception, (Problem) problem);
    }

    public RuntimeException throwError(RuntimeException exception, Problem problem) {
        report(problem);
        throw exception;
    }

    private void report(Problem problem) {
        buildOperationProgressEventEmitter.emitNowIfCurrent(problem);
    }
}
