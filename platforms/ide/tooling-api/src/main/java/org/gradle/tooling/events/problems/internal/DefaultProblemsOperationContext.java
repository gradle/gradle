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

package org.gradle.tooling.events.problems.internal;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.Failure;
import org.gradle.tooling.events.problems.AdditionalData;
import org.gradle.tooling.events.problems.Details;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.ProblemContext;
import org.gradle.tooling.events.problems.Solution;

import javax.annotation.Nullable;
import java.util.List;

@NonNullApi
public class DefaultProblemsOperationContext implements ProblemContext {
    private final Details details;
    private final List<Location> originLocations;
    private final List<Location> contextualLocations;
    private final List<Solution> solutions;
    private final AdditionalData additionalData;
    private final Failure failure;

    public DefaultProblemsOperationContext(
        @Nullable Details details,
        List<Location> originLocations,
        List<Location> contextualLocations,
        List<Solution> solutions,
        AdditionalData additionalData,
        @Nullable Failure failure
    ) {
        this.details = details;
        this.originLocations = originLocations;
        this.contextualLocations = contextualLocations;
        this.solutions = solutions;
        this.additionalData = additionalData;
        this.failure = failure;
    }

    @Nullable
    @Override
    public Details getDetails() {
        return details;
    }


    @Override
    public List<Location> getOriginLocations() {
        return originLocations;
    }

    @Override
    public List<Location> getContextualLocations() {
        return contextualLocations;
    }

    @Override
    public List<Solution> getSolutions() {
        return solutions;
    }

    public AdditionalData getAdditionalData() {
        return additionalData;
    }

    @Nullable
    @Override
    public Failure getFailure() {
        return failure;
    }
}
