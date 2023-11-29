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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.gradle.api.NonNullApi;
import org.gradle.internal.build.event.types.DefaultInternalProblemAggregation;
import org.gradle.internal.build.event.types.DefaultProblemAggregationDetails;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.InternalProblemAggregation;
import org.gradle.tooling.internal.protocol.InternalProblemDetails;
import org.gradle.tooling.internal.protocol.InternalProblemEvent;
import org.gradle.tooling.internal.protocol.problem.InternalBasicProblemDetails;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;

@NonNullApi
public class AggregatingProblemConsumer {
    private final Multimap<String, InternalProblemEvent> seenProblems = ArrayListMultimap.create();
    private final ProgressEventConsumer progressEventConsumer;
    private final Supplier<OperationIdentifier> operationIdentifierSupplier;

    AggregatingProblemConsumer(ProgressEventConsumer progressEventConsumer, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        this.progressEventConsumer = progressEventConsumer;
        this.operationIdentifierSupplier = operationIdentifierSupplier;
    }

    void sendProblemSummaries() {
        List<InternalProblemAggregation> problemSummaries = createSummaries();

        if (problemSummaries.isEmpty()) {
            return;
        }

        DefaultProblemEvent event = new DefaultProblemEvent(
            new DefaultProblemDescriptor(operationIdentifierSupplier.get(), CurrentBuildOperationRef.instance().getId()),
            new DefaultProblemAggregationDetails(problemSummaries));

        progressEventConsumer.progress(event);
        seenProblems.clear();
    }

    private List<InternalProblemAggregation> createSummaries() {
        return seenProblems.asMap().values()
            .stream()
            .map(ImmutableList::copyOf)
            .filter(values -> values.size() > 1)
            .map(aggregatedEvents -> {
                InternalBasicProblemDetails firstProblem = (InternalBasicProblemDetails) aggregatedEvents.iterator().next().getDetails();
                return new DefaultInternalProblemAggregation(firstProblem.getCategory(), firstProblem.getLabel(), aggregatedEvents);
            })
            .collect(toImmutableList());
    }

    void emit(InternalProblemEvent problem) {
        InternalProblemDetails details = problem.getDetails();
        if (!(details instanceof InternalBasicProblemDetails)) {
            return;
        }

        InternalBasicProblemDetails basicDetails = (InternalBasicProblemDetails) details;
        String aggregationKey = basicDetails.getCategory().getCategory() + ";" + basicDetails.getLabel().getLabel();

        if(seenProblems.size() > 100_000) {
            progressEventConsumer.progress(problem);
            sendProblemSummaries();
            return;
        }

        Collection<InternalProblemEvent> seenProblem = seenProblems.get(aggregationKey);
        if (!seenProblem.isEmpty()) {
            seenProblem.add(problem);
            return;
        }

        seenProblems.put(aggregationKey, problem);
        progressEventConsumer.progress(problem);
    }
}
