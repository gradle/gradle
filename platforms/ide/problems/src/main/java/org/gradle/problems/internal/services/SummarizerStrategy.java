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
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.ProblemSummaryData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class SummarizerStrategy {
    private final Map<ProblemId, ProblemSummaryInfo> seenProblemsWithCounts = new HashMap<>();
    private final int threshold;
    private final List<Pattern> suppressedProblemPatterns;


    public SummarizerStrategy(int threshold, List<String> suppressedProblemPatterns) {
        this.threshold = threshold;
        this.suppressedProblemPatterns = suppressedProblemPatterns.stream().map(pattern -> Pattern.compile(pattern)).collect(Collectors.toList());
    }

    synchronized List<ProblemSummaryData> getCutOffProblems() {
        return seenProblemsWithCounts.entrySet().stream()
            .filter(entry -> entry.getValue().getCount() > threshold)
            .map(entry -> new ProblemSummaryData(entry.getKey(), entry.getValue().getCount() - threshold))
            .collect(toImmutableList());
    }

    synchronized boolean shouldEmit(InternalProblem problem) {
        for (Pattern pattern : suppressedProblemPatterns) {
            if (pattern.matcher(problem.getDefinition().getId().toString()).matches()) {
                return false;
            }
        }
        ProblemSummaryInfo summaryInfo = seenProblemsWithCounts.computeIfAbsent(
            problem.getDefinition().getId(),
            key -> new ProblemSummaryInfo()
        );
        return summaryInfo.shouldEmit(problem.hashCode(), threshold);
    }
}
