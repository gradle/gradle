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
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemSummaryData;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * Decides which problems reach the consumers.
 * <p>
 * Each distinct problem is emitted once per reporting task. Contextual locations do not make a problem distinct, so the same problem reached through different call stacks counts once, unless it records its stack as an origin location.
 * <p>
 * Each task may emit at most {@code threshold} distinct problems per {@link ProblemId}, so a problem that many tasks report cannot use up the limit of the others.
 * Problems reported outside a task share one limit. Problems over the limit appear only as a count in the summary.
 */
public class SummarizerStrategy {
    private final Map<ProblemId, Map<@Nullable String, ProblemSummaryInfo>> seenProblemsWithCounts = new HashMap<>();
    private final int threshold;

    public SummarizerStrategy(int threshold) {
        this.threshold = threshold;
    }

    synchronized List<ProblemSummaryData> getCutOffProblems() {
        return seenProblemsWithCounts.entrySet().stream()
            .map(entry -> new ProblemSummaryData(entry.getKey(), cutOffCount(entry.getValue().values())))
            .filter(summary -> summary.getCount() > 0)
            .collect(toImmutableList());
    }

    private int cutOffCount(Collection<ProblemSummaryInfo> summaryInfos) {
        return summaryInfos.stream()
            .mapToInt(summaryInfo -> Math.max(summaryInfo.getCount() - threshold, 0))
            .sum();
    }

    synchronized boolean shouldEmit(ProblemInternal problem, @Nullable String taskPath) {
        return seenProblemsWithCounts
            .computeIfAbsent(problem.getDefinition().getId(), key -> new HashMap<>())
            .computeIfAbsent(taskPath, key -> new ProblemSummaryInfo())
            .shouldEmit(problem.hashCode(), threshold);
    }
}
