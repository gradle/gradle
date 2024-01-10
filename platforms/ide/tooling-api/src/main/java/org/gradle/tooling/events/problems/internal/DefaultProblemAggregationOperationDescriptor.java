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

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.problems.ProblemAggregation;
import org.gradle.tooling.events.problems.ProblemAggregationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

import java.util.List;

public class DefaultProblemAggregationOperationDescriptor extends DefaultOperationDescriptor implements ProblemAggregationDescriptor {
    private final List<ProblemAggregation> aggregations;

    public DefaultProblemAggregationOperationDescriptor(InternalOperationDescriptor internalDescriptor, OperationDescriptor parent, List<ProblemAggregation> aggregations) {
        super(internalDescriptor, parent);
        this.aggregations = aggregations;
    }

    @Override
    public List<ProblemAggregation> getAggregations() {
        return aggregations;
    }
}
