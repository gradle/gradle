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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemCategory;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.UnboundBasicProblemBuilder;
import org.gradle.api.problems.locations.ProblemLocation;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultProblem implements Problem, Serializable {
    private final String label;
    private Severity severity;
    private final List<ProblemLocation> where;
    private final DocLink documentationLink;
    private final String description;
    private final List<String> solutions;
    private final RuntimeException cause;
    private final String problemCategory;
    private final Map<String, String> additionalMetadata;

    @Nullable
    private OperationIdentifier buildOperationId;

    protected DefaultProblem(
        String label,
        Severity severity,
        List<ProblemLocation> locations,
        @Nullable DocLink documentationUrl,
        @Nullable String description,
        @Nullable List<String> solutions,
        @Nullable RuntimeException cause,
        String problemCategory,
        Map<String, String> additionalMetadata,
        @Nullable OperationIdentifier buildOperationId
    ) {
        this.label = label;
        this.severity = severity;
        this.where = ImmutableList.copyOf(locations);
        this.documentationLink = documentationUrl;
        this.description = description;
        this.solutions = solutions == null ? ImmutableList.<String>of() : ImmutableList.copyOf(solutions);
        this.cause = cause;
        this.problemCategory = problemCategory;
        this.additionalMetadata = ImmutableMap.copyOf(additionalMetadata);
        this.buildOperationId = buildOperationId;
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
    public List<ProblemLocation> getLocations() {
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
    public RuntimeException getException() { // TODO (donat) Investigate why this is represented as List<StackTraceElement> on DefaultDeprecatedUsageProgressDetails.
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

    public void setBuildOperationRef(@Nullable OperationIdentifier buildOperationId) {
        this.buildOperationId = buildOperationId;
    }

    @Nullable
    public OperationIdentifier getBuildOperationId() {
        return buildOperationId;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    @Override
    public UnboundBasicProblemBuilder toBuilder() {
        return new DefaultBasicProblemBuilder(this);
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
            equals(additionalMetadata, that.additionalMetadata) &&
            equals(buildOperationId, that.buildOperationId);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{label, severity, where, documentationLink, description, solutions, cause, additionalMetadata, buildOperationId});
    }

}
