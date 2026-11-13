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

import org.gradle.operations.problems.ProblemDefinition;
import org.gradle.operations.problems.ProblemLocation;
import org.gradle.operations.problems.ProblemSeverity;
import org.gradle.operations.problems.ProblemUsageProgressDetails;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultProblemProgressDetails implements ProblemProgressDetails, ProblemUsageProgressDetails {
    private final ProblemInternal problem;
    private final BuildOperationProblem buildOperationProblem;

    public DefaultProblemProgressDetails(ProblemInternal problem) {
        this.problem = problem;
        this.buildOperationProblem = new BuildOperationProblem(problem);
    }

    public ProblemInternal getProblem() {
        return problem;
    }

    @Override
    public ProblemDefinition getDefinition() {
        return buildOperationProblem.getDefinition();
    }

    @Override
    public ProblemSeverity getSeverity() {
        return buildOperationProblem.getSeverity();
    }

    @Nullable
    @Override
    public String getContextualLabel() {
        return buildOperationProblem.getContextualLabel();
    }

    @Override
    public List<String> getSolutions() {
        return buildOperationProblem.getSolutions();
    }

    @Nullable
    @Override
    public String getDetails() {
        return buildOperationProblem.getDetails();
    }

    @Override
    public List<ProblemLocation> getOriginLocations() {
        return buildOperationProblem.getOriginLocations();
    }

    @Override
    public List<ProblemLocation> getContextualLocations() {
        return buildOperationProblem.getContextualLocations();
    }

    @Nullable
    @Override
    public Throwable getFailure() {
        return problem.getException() == null ? null : problem.getException();
    }
}
