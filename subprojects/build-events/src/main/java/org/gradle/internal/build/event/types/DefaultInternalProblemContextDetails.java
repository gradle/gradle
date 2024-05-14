/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProblemContextDetails;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalContextualLabel;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalLocation;
import org.gradle.tooling.internal.protocol.problem.InternalSolution;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

@NonNullApi
public class DefaultInternalProblemContextDetails implements InternalProblemContextDetails, Serializable {
    private final InternalAdditionalData additionalData;
    @Nullable
    private final InternalDetails details;
    private final List<InternalLocation> locations;
    private final List<InternalSolution> solutions;
    private final InternalFailure failure;

    private final InternalContextualLabel contextualLabel;

    public DefaultInternalProblemContextDetails(InternalAdditionalData additionalData,
                                                @Nullable InternalDetails details,
                                                List<InternalLocation> locations,
                                                List<InternalSolution> solutions,
                                                @Nullable InternalFailure failure,
                                                @Nullable InternalContextualLabel contextualLabel
    ) {
        this.additionalData = additionalData;
        this.details = details;
        this.locations = locations;
        this.solutions = solutions;
        this.failure = failure;
        this.contextualLabel = contextualLabel;
    }

    @Override
    public InternalAdditionalData getAdditionalData() {
        return additionalData;
    }

    @Nullable
    @Override
    public InternalDetails getDetails() {
        return details;
    }

    @Override
    public List<InternalLocation> getLocations() {
        return locations;
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

    @Nullable
    @Override
    public InternalContextualLabel getContextualLabel() {
        return contextualLabel;
    }
}
