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

import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.problems.Problem;
import org.gradle.tooling.events.problems.ProblemId;
import org.gradle.tooling.events.problems.ProblemSummariesEvent;
import org.gradle.tooling.events.problems.SingleProblemEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProblemProgressListener implements ProgressListener {

    private final List<Problem> collectedProblems = new ArrayList<Problem>();
    private final Map<ProblemId, Integer> summaryCounts = new HashMap<>();

    @Override
    public void statusChanged(ProgressEvent progressEvent) {
        // If a problem with a specific ID doesn't happen too often, Gradle will send the problem as-is
        if (progressEvent instanceof SingleProblemEvent singleProblemEvent) {
            collectedProblems.add(singleProblemEvent.getProblem());
        }

        // On the other hand, if a certain problem (based on it's ID) is emitted over and over,
        // after a threshold, we stop sending all the data, and instead send a count.
        // (See DefaultProblemSummarizer for the logic)
        if (progressEvent instanceof ProblemSummariesEvent problemSummariesEvent) {
            for (var problemSummary : problemSummariesEvent.getProblemSummaries()) {
                summaryCounts.merge(
                    problemSummary.getProblemId(),
                    problemSummary.getCount(),
                    Integer::sum
                );
            }
        }
    }

    public List<Problem> getCollectedProblems() {
        return Collections.unmodifiableList(collectedProblems);
    }

    public Map<ProblemId, Integer> getSummaryCounts() {
        return Collections.unmodifiableMap(summaryCounts);
    }

}
