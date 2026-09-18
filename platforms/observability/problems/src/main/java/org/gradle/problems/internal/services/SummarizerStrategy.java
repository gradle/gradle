/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.gradle.api.problems.internal.TaskLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class SummarizerStrategy {
    private final Map<ProblemId, ProblemSummaryInfo> seenProblemsWithCounts = new HashMap<ProblemId, ProblemSummaryInfo>();
    private final int threshold;

    public SummarizerStrategy(int threshold) {
        this.threshold = threshold;
    }

    synchronized List<ProblemSummaryData> getCutOffProblems() {
        return seenProblemsWithCounts.entrySet().stream()
            .filter(entry -> entry.getValue().getCount() > threshold)
            .map(entry -> new ProblemSummaryData(entry.getKey(), entry.getValue().getCount() - threshold))
            .collect(toImmutableList());
    }

    synchronized boolean shouldEmit(ProblemInternal problem) {
        ProblemSummaryInfo summaryInfo = seenProblemsWithCounts.computeIfAbsent(
            problem.getDefinition().getId(),
            key -> new ProblemSummaryInfo()
        );
        return summaryInfo.shouldEmit(deduplicationHash(problem), threshold);
    }

    /**
     * The reporting task is the only contextual location that makes a problem distinct.
     * Others, like stack traces, only describe how the problem was reached.
     */
    private static int deduplicationHash(ProblemInternal problem) {
        int hash = problem.hashCode();
        for (ProblemLocation location : problem.getContextualLocations()) {
            if (location instanceof TaskLocation) {
                hash = 31 * hash + ((TaskLocation) location).getBuildTreePath().hashCode();
            }
        }
        return hash;
    }
}
