/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results;

import java.util.ArrayList;
import java.util.List;

public class ScenarioResult {

    enum Status {
        SUCCESS,
        FAILURE,
        UNKNOWN,
        STABLE_REGRESSION,
        SMALL_FLAKY_REGRESSION,
        BIG_FLAKY_REGRESSION
    }

    private final Status status;
    private final List<PerformanceTestExecutionResult> individualResults;
    private final List<PerformanceReportScenarioHistoryExecution> currentBuildExecutions = new ArrayList<>();
    private final List<PerformanceReportScenarioHistoryExecution> recentExecutions = new ArrayList<>();
    private final boolean crossVersion;

    public ScenarioResult(Status status, List<PerformanceTestExecutionResult> individualResults, boolean crossVersion) {
        this.status = status;
        this.individualResults = individualResults;
        this.crossVersion = crossVersion;
    }

    public Status getStatus() {
        return status;
    }

    public List<PerformanceTestExecutionResult> getIndividualResults() {
        return individualResults;
    }

    public List<PerformanceReportScenarioHistoryExecution> getCurrentBuildExecutions() {
        return currentBuildExecutions;
    }

    public List<PerformanceReportScenarioHistoryExecution> getRecentExecutions() {
        return recentExecutions;
    }

    public boolean isCrossVersion() {
        return crossVersion;
    }
}
