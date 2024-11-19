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

package org.gradle.problems.internal.services;

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.DefaultProblemsSummaryProgressDetails;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.api.problems.internal.ProblemReportCreator;
import org.gradle.api.problems.internal.ProblemSummarizer;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.gradle.internal.buildoption.IntegerInternalOption;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class DefaultProblemSummarizer implements ProblemSummarizer {

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Collection<ProblemEmitter> problemEmitters;
    private final Integer threshold;
    private final ProblemReportCreator problemReportCreator;

    private final Map<ProblemId, AtomicInteger> seenProblemsWithCounts = new HashMap<ProblemId, AtomicInteger>();

    public static final InternalOption<Integer> THRESHOLD_OPTION = new IntegerInternalOption("org.gradle.internal.problem.summary.threshold", 15);
    public static final int THRESHOLD_DEFAULT_VALUE = THRESHOLD_OPTION.getDefaultValue();

    public DefaultProblemSummarizer(
        BuildOperationProgressEventEmitter eventEmitter,
        CurrentBuildOperationRef currentBuildOperationRef,
        Collection<ProblemEmitter> problemEmitters,
        InternalOptions internalOptions,
        ProblemReportCreator problemReportCreator
    ) {
        this.eventEmitter = eventEmitter;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.problemEmitters = problemEmitters;
        this.threshold = internalOptions.getOption(THRESHOLD_OPTION).get();
        this.problemReportCreator = problemReportCreator;
    }

    @Override
    public String getId() {
        return "problem summarizer";
    }

    @Override
    public void report(File reportDir, ProblemConsumer validationFailures) {
        List<ProblemSummaryData> cutOffProblems = getCutOffProblems();
        problemReportCreator.createReportFile(reportDir, cutOffProblems);
        eventEmitter.emitNow(currentBuildOperationRef.getId(), new DefaultProblemsSummaryProgressDetails(cutOffProblems));
    }

    private List<ProblemSummaryData> getCutOffProblems() {
        return seenProblemsWithCounts.entrySet().stream()
            .filter(entry -> entry.getValue().get() > threshold)
            .map(entry -> new ProblemSummaryData(entry.getKey(), entry.getValue().get() - threshold))
            .collect(toImmutableList());
    }

    @Override
    public void emit(Problem problem, @Nullable OperationIdentifier id) {
        if (exceededThreshold(problem)) {
            return;
        }

        problemReportCreator.addProblem(problem);
        for (ProblemEmitter problemEmitter : problemEmitters) {
            problemEmitter.emit(problem, id);
        }
    }

    private boolean exceededThreshold(Problem problem) {
        ProblemId problemId = problem.getDefinition().getId();
        AtomicInteger count = seenProblemsWithCounts.computeIfAbsent(problemId, key -> new AtomicInteger(0));
        return count.incrementAndGet() > threshold;
    }
}
