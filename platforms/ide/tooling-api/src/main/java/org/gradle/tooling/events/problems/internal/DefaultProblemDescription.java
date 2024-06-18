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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.events.problems.AdditionalData;
import org.gradle.tooling.events.problems.ContextualLabel;
import org.gradle.tooling.events.problems.Details;
import org.gradle.tooling.events.problems.FailureContainer;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.ProblemDescription;
import org.gradle.tooling.events.problems.Solution;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class DefaultProblemDescription implements ProblemDescription {
    private final ProblemDefinition problemDefinition;
    private final ContextualLabel contextualLabel;
    private final Details problemDetails;
    private final List<Location> locations;
    private final List<Solution> solutions;
    private final AdditionalData additionalData;
    private final FailureContainer failureContainer;

    public DefaultProblemDescription(ProblemDefinition problemDefinition,
                                     ContextualLabel contextualLabel,
                                     Details problemDetails,
                                     List<Location> locations,
                                     List<Solution> solutions,
                                     AdditionalData additionalData,
                                     FailureContainer failureContainer) {

        this.problemDefinition = problemDefinition;
        this.contextualLabel = contextualLabel;
        this.problemDetails = problemDetails;
        this.locations = locations;
        this.solutions = solutions;
        this.additionalData = additionalData;
        this.failureContainer = failureContainer;
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
        return problemDetails;
    }

    @Override
    public List<Location> getLocations() {
        return locations;
    }

    @Override
    public List<Solution> getSolutions() {
        return solutions;
    }

    @Nullable
    @Override
    public FailureContainer getFailure() {
        return failureContainer;
    }

    public AdditionalData getAdditionalData() {
        return additionalData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblemDescription that = (DefaultProblemDescription) o;
        return Objects.equals(problemDefinition, that.problemDefinition)
            && Objects.equals(contextualLabel, that.contextualLabel)
            && Objects.equals(problemDetails, that.problemDetails)
            && Objects.equals(locations, that.locations)
            && Objects.equals(solutions, that.solutions)
            && Objects.equals(additionalData, that.additionalData)
            && Objects.equals(failureContainer, that.failureContainer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(problemDefinition, contextualLabel, problemDetails, locations, solutions, additionalData, failureContainer);
    }
}
