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

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.problems.AdditionalData;
import org.gradle.tooling.events.problems.ContextualLabel;
import org.gradle.tooling.events.problems.Details;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.Solution;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public class DefaultProblem implements Problem {
    private final ProblemDefinition problemDefinition;
    private final ContextualLabel contextualLabel;
    private final Details details;
    private final List<Location> originLocations;
    private final List<Location> contextualLocations;
    private final List<Solution> solutions;
    private final AdditionalData additionalData;
    private final Failure failure;

    public DefaultProblem(
        ProblemDefinition problemDefinition,
        ContextualLabel contextualLabel,
        Details details,
        List<Location> originLocations,
        List<Location> contextualLocations,
        List<Solution> solutions,
        AdditionalData additionalData,
        @Nullable Failure failure) {
        this.problemDefinition = problemDefinition;
        this.contextualLabel = contextualLabel;
        this.details = details;
        this.originLocations = originLocations;
        this.contextualLocations = contextualLocations;
        this.solutions = solutions;
        this.additionalData = additionalData;
        this.failure = failure;
    }

    @Override
    public ProblemDefinition getDefinition() {
        return problemDefinition;
    }

    @Override
    public ContextualLabel getContextualLabel() {
        return contextualLabel;
    }

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

    @Nullable
    @Override
    public Failure getFailure() {
        return failure;
    }

    @Override
    public AdditionalData getAdditionalData() {
        return additionalData;
    }
}
