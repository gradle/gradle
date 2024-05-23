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

import org.gradle.tooling.events.problems.ProblemAggregation;
import org.gradle.tooling.events.problems.ProblemContext;
import org.gradle.tooling.events.problems.ProblemDefinition;

import java.util.List;
import java.util.Objects;

public class DefaultProblemAggregation implements ProblemAggregation {

    ProblemDefinition problemDefinition;
    private final List<ProblemContext> problemContextDetails;

    public DefaultProblemAggregation(
        ProblemDefinition problemDefinition,
        List<ProblemContext> problemContextDetails
    ) {
        this.problemDefinition = problemDefinition;
        this.problemContextDetails = problemContextDetails;
    }

    @Override
    public ProblemDefinition getDefinition() {
        return problemDefinition;
    }

    @Override
    public List<ProblemContext> getProblemContext() {
        return problemContextDetails;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblemAggregation that = (DefaultProblemAggregation) o;
        return Objects.equals(problemDefinition, that.problemDefinition) && Objects.equals(problemContextDetails, that.problemContextDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(problemDefinition, problemContextDetails);
    }
}
