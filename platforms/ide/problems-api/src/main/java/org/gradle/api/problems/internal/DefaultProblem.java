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

import com.google.common.base.Objects;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

import static com.google.common.base.Objects.equal;

@NonNullApi
public class DefaultProblem implements Serializable, InternalProblem {
    private final ProblemDefinition problemDefinition;
    private final String contextualLabel;
    private final List<String> solutions;
    private final List<ProblemLocation> originLocations;
    private final List<ProblemLocation> contextualLocations;
    private final String details;
    private final Throwable exception;
    private final AdditionalData additionalData;

    public DefaultProblem(
        ProblemDefinition problemDefinition,
        @Nullable String contextualLabel,
        List<String> solutions,
        List<ProblemLocation> originLocations,
        List<ProblemLocation> contextualLocations,
        @Nullable String details,
        @Nullable Throwable exception,
        @Nullable AdditionalData additionalData
    ) {
        this.problemDefinition = problemDefinition;
        this.contextualLabel = contextualLabel;
        this.solutions = solutions;
        this.originLocations = originLocations;
        this.contextualLocations = contextualLocations;
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
    public List<ProblemLocation> getOriginLocations() {
        return originLocations;
    }

    @Override
    public List<ProblemLocation> getContextualLocations() {
        return contextualLocations;
    }

    @Nullable
    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    @Nullable
    public AdditionalData getAdditionalData() {
        return additionalData;
    }

    @Override
    public InternalProblemBuilder toBuilder(AdditionalDataBuilderFactory additionalDataBuilderFactory, Instantiator instantiator, PayloadSerializer payloadSerializer) {
        return new DefaultProblemBuilder(this, additionalDataBuilderFactory, instantiator, payloadSerializer);
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
        return equal(problemDefinition, that.problemDefinition) &&
            equal(contextualLabel, that.contextualLabel) &&
            equal(solutions, that.solutions) &&
            equal(originLocations, that.originLocations) &&
            equal(details, that.details) &&
            equal(exception, that.exception) &&
            equal(additionalData, that.additionalData);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(problemDefinition, contextualLabel, solutions, originLocations, details, exception, additionalData);
    }

}
