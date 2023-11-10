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
import org.gradle.api.problems.BasicProblemBuilder;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.api.problems.Severity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builder for problems.
 *
 * @since 8.3
 */
@Incubating
public class DefaultReportableProblemBuilder extends BaseProblemBuilder implements BasicProblemBuilder {

    private final InternalProblems problemsService;

    public DefaultReportableProblemBuilder(InternalProblems problemsService) {
        this.problemsService = problemsService;
    }

    public ReportableProblem build() {
        if (!explicitlyUndocumented && documentationUrl == null) {
            throw new IllegalStateException("Problem is not documented: " + label);
        }

        return new DefaultReportableProblem(
            label,
            getSeverity(severity),
            locations,
            documentationUrl,
            description,
            solution,
            exception == null && collectLocation ? new Exception() : exception, //TODO: don't create exception if already reported often
            problemCategory,
            additionalMetadata,
            problemsService);
    }

    // TODO check if we need the methods below

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

    RuntimeException getException() {
        return exception;
    }
}
