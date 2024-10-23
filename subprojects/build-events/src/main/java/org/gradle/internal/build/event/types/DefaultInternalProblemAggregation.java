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
import org.gradle.tooling.internal.protocol.InternalProblemAggregationVersion3;
import org.gradle.tooling.internal.protocol.InternalProblemContextDetails;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;

import java.io.Serializable;
import java.util.List;

@NonNullApi
public class DefaultInternalProblemAggregation implements InternalProblemAggregationVersion3, Serializable {

    private final InternalProblemDefinition definition;
    private final List<InternalProblemContextDetails> problemEvents;

    public DefaultInternalProblemAggregation(InternalProblemDefinition definition,
                                             List<InternalProblemContextDetails> problemEvents) {
        this.definition = definition;
        this.problemEvents = problemEvents;
    }

    @Override
    public InternalProblemDefinition getProblemDefinition() {
        return definition;
    }

    @Override
    public List<InternalProblemContextDetails> getProblemContextDetails() {
        return problemEvents;
    }
}
