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

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@NonNullApi
public class DefaultProblem implements Serializable, Problem {
    private final ProblemDefinition problemDefinition;
    private final String contextualLabel;
    private final List<String> solutions;
    private final List<ProblemLocation> problemLocations;
    private final String details;
    private final RuntimeException exception;
    private final AdditionalData additionalData;

    protected DefaultProblem(
        ProblemDefinition problemDefinition,
        @Nullable String contextualLabel,
        List<String> solutions,
        List<ProblemLocation> problemLocations,
        @Nullable String details,
        RuntimeException exception,
        @Nullable AdditionalData additionalData
    ) {
        this.problemDefinition = problemDefinition;
        this.contextualLabel = contextualLabel;
        this.solutions = solutions;
        this.problemLocations = problemLocations;
        this.details = details;
        this.exception = exception;
        this.additionalData = additionalData;
    }

    @Override
    public ProblemDefinition getDefinition() {
        return problemDefinition;
    }

    @Nullable
    @Override
    public String getContextualLabel() {
        return contextualLabel;
    }

    @Override
    public List<String> getSolutions() {
        return solutions;
    }

    @Nullable
    @Override
    public String getDetails() {
        return details;
    }

    @Override
    public List<ProblemLocation> getLocations() {
        return problemLocations;
    }

    @Nullable
    @Override
    public RuntimeException getException() {
        return exception;
    }

    @Override
    public AdditionalData getAdditionalData() {
        return additionalData;
    }

    @Override
    public InternalProblemBuilder toBuilder() {
        return new DefaultProblemBuilder(this);
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
        return equals(problemDefinition, that.problemDefinition) &&
            equals(contextualLabel, that.contextualLabel) &&
            equals(solutions, that.solutions) &&
            equals(problemLocations, that.problemLocations) &&
            equals(details, that.details) &&
            equals(exception, that.exception) &&
            equals(additionalData, that.additionalData);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{problemDefinition, contextualLabel});
    }

}
