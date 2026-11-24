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

package org.gradle.internal.build.event.types;

import com.google.common.collect.ImmutableList;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion4;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion5;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;
import org.gradle.tooling.internal.protocol.problem.InternalTrace;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

@NullMarked
public class DefaultProblemDetails implements InternalBasicProblemDetailsVersion3, InternalBasicProblemDetailsVersion4, InternalBasicProblemDetailsVersion5, Serializable {
    private final InternalProblemDefinition definition;
    private final InternalDetails details;
    @Nullable
    private final InternalContextualLabel contextualLabel;
    private final List<InternalLocation> originLocations;
    private final List<InternalLocation> contextualLocations;
    private final List<InternalSolution> solutions;
    private InternalAdditionalData additionalData;
    private final InternalFailure failure;
    @Nullable
    private final InternalTrace detectionTrace;
    private final List<InternalTrace> originTraces;

    public DefaultProblemDetails(
        InternalProblemDefinition definition,
        @Nullable InternalDetails details,
        @Nullable InternalContextualLabel contextualLabel,
        List<InternalLocation> originLocations,
        List<InternalLocation> contextualLocations,
        List<InternalSolution> solutions,
        InternalAdditionalData additionalData,
        @Nullable InternalFailure failure,
        @Nullable InternalTrace detectionTrace,
        List<InternalTrace> originTraces
    ) {
        this.definition = definition;
        this.details = details;
        this.contextualLabel = contextualLabel;
        this.originLocations = originLocations;
        this.contextualLocations = contextualLocations;
        this.solutions = solutions;
        this.additionalData = additionalData;
        this.failure = failure;
        this.detectionTrace = detectionTrace;
        this.originTraces = originTraces;
    }

    @Override
    public InternalDetails getDetails() {
        return details;
    }

    @Nullable
    @Override
    public InternalContextualLabel getContextualLabel() {
        return contextualLabel;
    }

    @Override
    public List<InternalLocation> getLocations() {
        return ImmutableList
            .<InternalLocation>builder()
            .addAll(originLocations)
            .addAll(contextualLocations)
            .build();
    }

    @Override
    public List<InternalLocation> getOriginLocations() {
        return originLocations;
    }

    @Override
    public List<InternalLocation> getContextualLocations() {
        return contextualLocations;
    }

    @Override
    public InternalProblemDefinition getDefinition() {
        return definition;
    }


    @Override
    public List<InternalSolution> getSolutions() {
        return solutions;
    }

    @Nullable
    @Override
    public InternalFailure getFailure() {
        return failure;
    }

    @Override
    public InternalAdditionalData getAdditionalData() {
        return additionalData;
    }

    @Override
    public List<InternalTrace> getOriginTraces() {
        return originTraces;
    }

    @Nullable
    @Override
    public InternalTrace getDetectionTrace() {
        return detectionTrace;
    }

    public void setAdditionalData(InternalAdditionalData additionalData) {
        this.additionalData = additionalData;
    }

}
