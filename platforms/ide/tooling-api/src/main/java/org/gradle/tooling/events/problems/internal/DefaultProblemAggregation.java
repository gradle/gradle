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

import org.gradle.tooling.events.problems.Label;
import org.gradle.tooling.events.problems.ProblemAggregation;
import org.gradle.tooling.events.problems.ProblemCategory;
import org.gradle.tooling.events.problems.ProblemDescriptor;

import java.util.List;

public class DefaultProblemAggregation implements ProblemAggregation {

    private final ProblemCategory problemCategory;
    private final Label problemLabel;
    private final List<ProblemDescriptor> problemDescriptors;

    public DefaultProblemAggregation(
        ProblemCategory problemCategory,
        Label problemLabel, List<ProblemDescriptor> problemDescriptors
    ) {
        this.problemDescriptors = problemDescriptors;
        this.problemCategory = problemCategory;
        this.problemLabel = problemLabel;
    }

    @Override
    public ProblemCategory getCategory() {
        return problemCategory;
    }

    @Override
    public Label getLabel() {
        return problemLabel;
    }

    @Override
    public List<ProblemDescriptor> getProblemDescriptors() {
        return problemDescriptors;
    }
}
