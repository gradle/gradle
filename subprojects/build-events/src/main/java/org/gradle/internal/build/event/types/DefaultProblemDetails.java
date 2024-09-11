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

import org.gradle.api.NonNullApi;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

@NonNullApi
public class DefaultProblemDetails implements InternalBasicProblemDetailsVersion3, Serializable {
    private final InternalProblemDefinition definition;
    private final InternalDetails details;
    @Nullable
    private final InternalContextualLabel contextualLabel;
    private final List<InternalLocation> locations;
    private final List<InternalSolution> solutions;
    private final InternalAdditionalData additionalData;
    private final InternalFailure failure;

    public DefaultProblemDetails(
        InternalProblemDefinition definition,
        @Nullable InternalDetails details,
        @Nullable InternalContextualLabel contextualLabel,
        List<InternalLocation> locations,
        List<InternalSolution> solutions,
        InternalAdditionalData additionalData,
        @Nullable InternalFailure failure
    ) {
        this.definition = definition;
        this.details = details;
        this.contextualLabel = contextualLabel;
        this.locations = locations;
        this.solutions = solutions;
        this.additionalData = additionalData;
        this.failure = failure;
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
        return locations;
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
}
