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
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemCategory;
import org.gradle.api.problems.locations.ProblemLocation;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NonNullApi
public class DefaultProblem implements Problem {
    private String label;
    private Severity severity;
    private Set<ProblemLocation> where;
    private DocLink documentationLink;
    private String description;
    private List<String> solutions;
    private Throwable cause;
    private String problemCategory;
    private Map<String, String> additionalMetadata;

    public DefaultProblem(
        String label,
        Severity severity,
        Set<ProblemLocation> locations,
        @Nullable DocLink documentationUrl,
        @Nullable String description,
        @Nullable List<String> solutions,
        @Nullable Throwable cause,
        String problemCategory,
        Map<String, String> additionalMetadata
    ) {
        this.label = label;
        this.severity = severity;
        this.where = locations;
        this.documentationLink = documentationUrl;
        this.description = description;
        this.solutions = solutions == null ? Collections.<String>emptyList() : solutions;
        this.cause = cause;
        this.problemCategory = problemCategory;
        this.additionalMetadata = additionalMetadata;
    }

    public DefaultProblem() {
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public Set<ProblemLocation> getWhere() {
        return where;
    }

    @Nullable
    @Override
    public DocLink getDocumentationLink() {
        return documentationLink;
    }

    @Override
    public String getDetails() {
        return description;
    }

    @Override
    public List<String> getSolutions() {
        return solutions;
    }

    @Override
    public Throwable getException() { // TODO (donat) Investigate why this is represented as List<StackTraceElement> on DefaultDeprecatedUsageProgressDetails.
        return cause;
    }

    @Override
    public ProblemCategory getProblemCategory() {
        return new DefaultProblemCategory(problemCategory);
    }

    @Override
    public Map<String, String> getAdditionalData() {
        return additionalMetadata;
    }

    private static boolean equals(@Nullable Object a, @Nullable Object b) {
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
        return equals(label, that.label) &&
            severity == that.severity &&
            equals(where, that.where) &&
            equals(problemCategory, that.problemCategory) &&
            equals(documentationLink, that.documentationLink) &&
            equals(description, that.description) &&
            equals(solutions, that.solutions) &&
            equals(cause, that.cause) &&
            equals(additionalMetadata, that.additionalMetadata);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{label, severity, where, documentationLink, description, solutions, cause, additionalMetadata});
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }
}
