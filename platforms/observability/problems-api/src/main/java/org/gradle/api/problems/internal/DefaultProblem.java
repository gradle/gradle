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
import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.ProblemDefinition;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.internal.location.Trace;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

import static com.google.common.base.Objects.equal;

@NullMarked
public class DefaultProblem implements Serializable, ProblemInternal {
    private final ProblemDefinition problemDefinition;
    private final String contextualLabel;
    private final List<String> solutions;
    private final List<ProblemLocation> originLocations;
    private final List<ProblemLocation> contextualLocations;
    private final String details;
    private final Throwable exception;
    private final AdditionalData additionalData;
    private final boolean writtenToConsole;
    private final Trace detectionTrace;
    private final List<Trace> originTraces;

    public DefaultProblem(
        ProblemDefinition problemDefinition,
        @Nullable String contextualLabel,
        List<String> solutions,
        List<ProblemLocation> originLocations,
        List<ProblemLocation> contextualLocations
    ) {
        this(problemDefinition, contextualLabel, solutions, originLocations, contextualLocations, null, null, null, false);
    }

    public DefaultProblem(
        ProblemDefinition problemDefinition,
        @Nullable String contextualLabel,
        List<String> solutions,
        List<ProblemLocation> originLocations,
        List<ProblemLocation> contextualLocations,
        @Nullable String details,
        @Nullable Throwable exception,
        @Nullable AdditionalData additionalData,
        boolean writtenToConsole
    ) {
        this(problemDefinition, contextualLabel, solutions, originLocations, contextualLocations, details, exception, additionalData, writtenToConsole, null);
    }

    public DefaultProblem(
        ProblemDefinition problemDefinition,
        @Nullable String contextualLabel,
        List<String> solutions,
        List<ProblemLocation> originLocations,
        List<ProblemLocation> contextualLocations,
        @Nullable String details,
        @Nullable Throwable exception,
        @Nullable AdditionalData additionalData,
        boolean writtenToConsole,
        @Nullable Trace detectionTrace
    ) {
        this(problemDefinition, contextualLabel, solutions, originLocations, contextualLocations, details, exception, additionalData, writtenToConsole, detectionTrace, ImmutableList.<Trace>of());
    }

    public DefaultProblem(
        ProblemDefinition problemDefinition,
        @Nullable String contextualLabel,
        List<String> solutions,
        List<ProblemLocation> originLocations,
        List<ProblemLocation> contextualLocations,
        @Nullable String details,
        @Nullable Throwable exception,
        @Nullable AdditionalData additionalData,
        boolean writtenToConsole,
        @Nullable Trace detectionTrace,
        List<Trace> originTraces
    ) {
        this.problemDefinition = problemDefinition;
        this.contextualLabel = contextualLabel;
        this.solutions = solutions;
        this.originLocations = originLocations;
        this.contextualLocations = contextualLocations;
        this.details = details;
        this.exception = exception;
        this.additionalData = additionalData;
        this.writtenToConsole = writtenToConsole;
        this.detectionTrace = detectionTrace;
        this.originTraces = originTraces;
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

    @Override
    public List<Trace> getOriginTraces() {
        return originTraces;
    }

    @Nullable
    @Override
    public Trace getDetectionTrace() {
        return detectionTrace;
    }

    @Nullable
    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    public boolean isWrittenToConsole() {
        return writtenToConsole;
    }

    @Override
    @Nullable
    public AdditionalData getAdditionalData() {
        return additionalData;
    }

    @Override
    public ProblemBuilderInternal toBuilder(ProblemsInfrastructure infrastructure) {
        return new DefaultProblemBuilder(this, infrastructure);
    }

    @Override
    public ProblemInternal withId(ProblemId id) {
        return new DefaultProblem(
            new DefaultProblemDefinition(id, problemDefinition.getSeverity(), problemDefinition.getDocumentationLink()),
            contextualLabel,
            solutions,
            originLocations,
            contextualLocations,
            details,
            exception,
            additionalData,
            writtenToConsole,
            detectionTrace,
            originTraces
        );
    }

    @Override
    public ProblemInternal withDetectionTrace(Trace detectionTrace) {
        return new DefaultProblem(
            problemDefinition,
            contextualLabel,
            solutions,
            originLocations,
            contextualLocations,
            details,
            exception,
            additionalData,
            writtenToConsole,
            detectionTrace,
            originTraces
        );
    }

    @Override
    public ProblemInternal withOriginTraces(List<Trace> originTraces) {
        return new DefaultProblem(
            problemDefinition,
            contextualLabel,
            solutions,
            originLocations,
            contextualLocations,
            details,
            exception,
            additionalData,
            writtenToConsole,
            detectionTrace,
            originTraces
        );
    }

    /**
     * Two problems are the same when they say the same thing about the same cause.
     * <p>
     * The cause is the flat origin locations and the authored frames of the origin traces: the file line, the property, the class.
     * Build model units, such as the task at the root of a trace, don't make a problem distinct, and neither do the detection trace
     * and the contextual locations: summarization keeps problems of different tasks apart on its own.
     */
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
            equal(authoredOrigins(), that.authoredOrigins()) &&
            equal(details, that.details) &&
            equal(exception, that.exception) &&
            equal(identifyingData(), that.identifyingData()) &&
            writtenToConsole == that.writtenToConsole;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(problemDefinition, contextualLabel, solutions, originLocations, authoredOrigins(), details, exception, identifyingData(), writtenToConsole);
    }

    private List<List<Object>> authoredOrigins() {
        ImmutableList.Builder<List<Object>> origins = ImmutableList.builder();
        for (Trace trace : originTraces) {
            origins.add(trace.getAuthoredIdentity());
        }
        return origins.build();
    }

    // The text form of a Configuration Cache trace names the task that holds the state. It is a location, which the origin trace now carries, so it doesn't identify the problem.
    @Nullable
    private AdditionalData identifyingData() {
        return additionalData instanceof PropertyTraceData && !originTraces.isEmpty() ? null : additionalData;
    }

    @Override
    public String toString() {
        return "DefaultProblem{" +
            "problemDefinition=" + problemDefinition +
            ", contextualLabel='" + contextualLabel + '\'' +
            ", solutions=" + solutions +
            ", originLocations=" + originLocations +
            ", contextualLocations=" + contextualLocations +
            ", originTraces=" + originTraces +
            ", detectionTrace=" + detectionTrace +
            ", details='" + details + '\'' +
            ", exception=" + (exception != null ? exception.toString() : "null") +
            ", additionalData=" + additionalData +
            '}';
    }
}
