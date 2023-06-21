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

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemGroup;
import org.gradle.api.problems.interfaces.ProblemLocation;
import org.gradle.api.problems.interfaces.Severity;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultProblem implements Problem {

    private final ProblemGroup problemGroup;
    private final String message;
    private final Severity severity;
    private final ProblemLocation where;
    private final DocLink documentationLink;
    private final String description;
    private final List<String> solutions;
    private final Throwable cause;
    private final String problemType;
    private final Map<String, String> additionalMetadata;

    public DefaultProblem(ProblemGroup problemGroup, String message, Severity severity, @Nullable ProblemLocation location, @Nullable DocLink documentationUrl,
                          @Nullable String description, @Nullable List<String> solutions, @Nullable Throwable cause, String problemType, Map<String, String> additionalMetadata) {
        this.problemGroup = problemGroup;
        this.message = message;
        this.severity = severity;
        this.where = location;
        this.documentationLink = documentationUrl;
        this.description = description;
        this.solutions = solutions == null ? Collections.<String>emptyList() : solutions;
        this.cause = cause;
        this.problemType = problemType;
        this.additionalMetadata = additionalMetadata;
    }

    @Override
    public ProblemGroup getProblemGroup() {
        return problemGroup;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public ProblemLocation getWhere() {
        return where;
    }

    @Nullable
    @Override
    public DocLink getDocumentationLink() {
        return documentationLink;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<String> getSolutions() {
        return solutions;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String getProblemType() {
        return problemType;
    }

    @Override
    public Map<String, String> getAdditionalMetadata() {
        return additionalMetadata;
    }

    private static boolean equals(@Nullable  Object a, @Nullable Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblem that = (DefaultProblem) o;
        return equals(problemGroup, that.problemGroup) &&
            equals(message, that.message) &&
            severity == that.severity &&
            equals(where, that.where) &&
            equals(documentationLink, that.documentationLink) &&
            equals(description, that.description) &&
            equals(solutions, that.solutions) &&
            equals(cause, that.cause) &&
            equals(additionalMetadata, that.additionalMetadata);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{problemGroup, message, severity, where, documentationLink, description, solutions, cause, additionalMetadata});
    }
}
