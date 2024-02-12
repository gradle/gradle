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
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@NonNullApi
public class DefaultProblemDefinition implements Serializable, ProblemDefinition {
    private final String label;
    private Severity severity;
    private final DocLink documentationLink;
    private final List<String> solutions;
    private final ProblemCategory problemCategory;

    DefaultProblemDefinition(
        String label,
        Severity severity,
        @Nullable DocLink documentationUrl,
        @Nullable List<String> solutions,
        ProblemCategory problemCategory
    ) {
        this.label = label;
        this.severity = severity;
        this.documentationLink = documentationUrl;
        this.solutions = solutions == null ? ImmutableList.<String>of() : ImmutableList.copyOf(solutions);
        this.problemCategory = problemCategory;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Nullable
    @Override
    public DocLink getDocumentationLink() {
        return documentationLink;
    }

    @Override
    public List<String> getSolutions() {
        return solutions;
    }

    @Override
    public ProblemCategory getCategory() {
        return problemCategory;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
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
        DefaultProblemDefinition that = (DefaultProblemDefinition) o;
        return equals(label, that.label) &&
            severity == that.severity &&
            equals(problemCategory, that.problemCategory) &&
            equals(documentationLink, that.documentationLink) &&
            equals(solutions, that.solutions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{label, severity, documentationLink, solutions});
    }

}
