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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.ProblemId;
import org.gradle.internal.Pair;
import org.gradle.internal.buildoption.IntegerInternalOption;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.problems.buildtree.ProblemReporter;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultProblemSummarizer implements ProblemReporter, ProblemSummarizer {

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Collection<ProblemEmitter> problemEmitters;
    private final Integer threshold;

    private final Map<ProblemId, AtomicInteger> seenProblemsWithCounts = new HashMap<ProblemId, AtomicInteger>();

    public static final InternalOption<Integer> THRESHOLD_OPTION = new IntegerInternalOption("org.gradle.internal.problem.summary.threshold", 15);
    public static final int THRESHOLD_DEFAULT_VALUE = THRESHOLD_OPTION.getDefaultValue();

    public DefaultProblemSummarizer(BuildOperationProgressEventEmitter eventEmitter, CurrentBuildOperationRef currentBuildOperationRef, Collection<ProblemEmitter> problemEmitters, InternalOptions internalOptions) {
        this.eventEmitter = eventEmitter;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.problemEmitters = problemEmitters;
        this.threshold = internalOptions.getOption(THRESHOLD_OPTION).get();
    }

    @Override
    public String getId() {
        return "problem summarizer";
    }

    @Override
    public void report(File reportDir, ProblemConsumer validationFailures) {
        List<Pair<ProblemId, Integer>> cutOffProblems = getCutOffProblems();
        eventEmitter.emitNow(currentBuildOperationRef.getId(), new DefaultProblemsSummaryProgressDetails(cutOffProblems));
    }

    private List<Pair<ProblemId, Integer>> getCutOffProblems() {
        List<Pair<ProblemId, Integer>> cutOffProblems = new ArrayList<Pair<ProblemId, Integer>>();
        for (Map.Entry<ProblemId, AtomicInteger> entry : seenProblemsWithCounts.entrySet()) {
            if (entry.getValue().get() > threshold) {
                cutOffProblems.add(Pair.of(entry.getKey(), entry.getValue().get() - threshold));
            }
        }
        return cutOffProblems;
    }

    @Override
    public void emit(Problem problem, @Nullable OperationIdentifier id) {
        if (exceededThreshold(problem)) {
            return;
        }

        for (ProblemEmitter problemEmitter : problemEmitters) {
            problemEmitter.emit(problem, id);
        }
    }

    private boolean exceededThreshold(Problem problem) {
        ProblemId problemId = problem.getDefinition().getId();
        //TODO (Reinhold) Is summarization by problem id enough?
        AtomicInteger count = seenProblemsWithCounts.get(problemId);
        if (count == null) {
            count = new AtomicInteger(0);
            seenProblemsWithCounts.put(problemId, count);
        }
        int countValue = count.incrementAndGet();
        return countValue > threshold;
    }
}
