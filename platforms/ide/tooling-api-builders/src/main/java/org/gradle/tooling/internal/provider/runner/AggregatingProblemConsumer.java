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
import org.gradle.internal.build.event.types.DefaultInternalProblemContextDetails;
import org.gradle.internal.build.event.types.DefaultProblemAggregationDetails;
import org.gradle.internal.build.event.types.DefaultProblemDescriptor;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalProblemAggregationVersion3;
import org.gradle.tooling.internal.protocol.InternalProblemContextDetails;
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.InternalProblemEventVersion2;
import org.gradle.tooling.internal.protocol.InternalProblemGroup;
import org.gradle.tooling.internal.protocol.InternalProblemId;
import org.gradle.tooling.internal.protocol.problem.InternalProblemDetailsVersion2;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableList.toImmutableList;

@NonNullApi
public class AggregatingProblemConsumer {
    private final Multimap<String, InternalProblemEventVersion2> seenProblems = ArrayListMultimap.create();
    private final ProgressEventConsumer progressEventConsumer;
    private final Supplier<OperationIdentifier> operationIdentifierSupplier;
    private int thresholdForIntermediateSummary = 10_000;

    AggregatingProblemConsumer(ProgressEventConsumer progressEventConsumer, Supplier<OperationIdentifier> operationIdentifierSupplier) {
        this.progressEventConsumer = progressEventConsumer;
        this.operationIdentifierSupplier = operationIdentifierSupplier;
    }

    void setThresholdForIntermediateSummary(int thresholdForIntermediateSummary) {
        this.thresholdForIntermediateSummary = thresholdForIntermediateSummary;
    }

    void sendProblemSummaries() {
        List<InternalProblemAggregationVersion3> problemSummaries = createSummaries();

        if (problemSummaries.isEmpty()) {
            seenProblems.clear();
            return;
        }

        problemSummaries.forEach(summary -> {
            List<InternalProblemContextDetails> problemContextDetails = summary.getProblemContextDetails();
            progressEventConsumer.progress(new DefaultProblemEvent(
                new DefaultProblemDescriptor(operationIdentifierSupplier.get(), CurrentBuildOperationRef.instance().getId()),
                new DefaultProblemAggregationDetails(
                    summary.getProblemDefinition(),
                    problemContextDetails.stream().skip(1).collect(toImmutableList()))));
        });
        seenProblems.clear();
    }

    private List<InternalProblemAggregationVersion3> createSummaries() {
        return seenProblems.asMap().values()
            .stream()
            .map(ImmutableList::copyOf)
            .filter(values -> values.size() > 1)
            .map(AggregatingProblemConsumer::createProblemAggregation)
            .collect(toImmutableList());
    }

    private static DefaultInternalProblemAggregation createProblemAggregation(List<InternalProblemEventVersion2> aggregatedEvents) {
        InternalProblemDetailsVersion2 details = aggregatedEvents.iterator().next().getDetails();
        if (!(details instanceof InternalBasicProblemDetailsVersion3)) {
            throw new UnsupportedOperationException("Unsupported problem details: " + details.getClass().getName());
        }
        InternalBasicProblemDetailsVersion3 detailsV3 = (InternalBasicProblemDetailsVersion3) details;
        List<InternalProblemContextDetails> aggregatedContextDetails = aggregatedEvents.stream().map(event -> {
            InternalProblemDetailsVersion2 detailsV2 = event.getDetails();
            if (detailsV2 instanceof InternalBasicProblemDetailsVersion3) {
                InternalBasicProblemDetailsVersion3 basicDetails = (InternalBasicProblemDetailsVersion3) detailsV2;
                return new DefaultInternalProblemContextDetails(
                    basicDetails.getAdditionalData(),
                    basicDetails.getDetails(),
                    basicDetails.getLocations(),
                    basicDetails.getSolutions(),
                    basicDetails.getFailure(),
                    basicDetails.getContextualLabel()
                );
            } else {
                throw new UnsupportedOperationException("Unsupported problem details: " + detailsV2.getClass().getName());
            }
    }).collect(toImmutableList());
        return new DefaultInternalProblemAggregation(detailsV3.getDefinition(), aggregatedContextDetails);
    }

    void emit(InternalProblemEventVersion2 problem) {
        InternalProblemDetailsVersion2 details = problem.getDetails();
        if (!(details instanceof InternalBasicProblemDetailsVersion3)) {
            throw new UnsupportedOperationException("Unsupported problem details: " + details.getClass().getName());
        }
        InternalBasicProblemDetailsVersion3 d = (InternalBasicProblemDetailsVersion3) details;
        InternalProblemDefinition definition = d.getDefinition();
        String aggregationKey = aggregationKeyFor(definition.getId());
        sendProgress(problem, aggregationKey);

        if (seenProblems.size() > thresholdForIntermediateSummary) {
            sendProblemSummaries();
        }
    }

    private static String aggregationKeyFor(InternalProblemId id) {
        return aggregationKeyFor(id.getGroup()) + ";" + id.getName();
    }

    private static String aggregationKeyFor(InternalProblemGroup group) {
        return group.getParent() == null ? group.getName() : aggregationKeyFor(group.getParent()) + ";" + group.getName();
    }

    private void sendProgress(InternalProblemEventVersion2 problem, String aggregationKey) {
        Collection<InternalProblemEventVersion2> seenProblem = seenProblems.get(aggregationKey);
        if (seenProblem.isEmpty()) {
            seenProblems.put(aggregationKey, problem);
            progressEventConsumer.progress(problem);
        } else {
            seenProblem.add(problem);
        }
    }

}
