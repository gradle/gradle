/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.performance.results

/**
 * Represents a row in performance report, i.e. a specific scenario.
 */
class PerformanceReportScenario {
    final PerformanceExperiment performanceExperiment
    /**
     * The executions read from TeamCity-build-generated-result-JSONs.
     */
    final List<PerformanceTestExecutionResult> teamCityExecutions

    /**
     * The execution read from performance database which has the same TC build id as `teamCityExecutions`.
     */
    final List<PerformanceReportScenarioHistoryExecution> currentExecutions

    /**
     * The execution read from performance database, excluding current executions
     */
    final List<PerformanceReportScenarioHistoryExecution> historyExecutions

    final boolean crossBuild

    PerformanceReportScenario(
        List<PerformanceTestExecutionResult> teamCityExecutions,
        List<PerformanceReportScenarioHistoryExecution> historyExecutions,
        boolean crossBuild,
        Set<String> pipelineBuildIds,
        String currentCommit
    ) {
        if (teamCityExecutions.empty) {
            throw new IllegalArgumentException("teamCity executions must not be empty!")
        }
        this.performanceExperiment = teamCityExecutions[0].performanceExperiment
        this.teamCityExecutions = teamCityExecutions
        this.crossBuild = crossBuild

        // "Current" executions are the ones this pipeline actually produced. On CI we identify them by matching each DB
        // row's own teamCityBuildId (written accurately by the run that measured it) against the authoritative bucket
        // build IDs of this pipeline; a build-cache hit produces no DB row at all, so cached results never appear here.
        // The result JSON no longer carries a build id, so locally (authoritative set unknown) we match the commit.
        this.currentExecutions = pipelineBuildIds.isEmpty()
            ? historyExecutions.findAll { it.commitId == currentCommit }
            : historyExecutions.findAll { pipelineBuildIds.contains(it.teamCityBuildId) }
        this.historyExecutions = historyExecutions
        this.fromCache = !pipelineBuildIds.isEmpty() && currentExecutions.empty
    }

    /**
     * True when this pipeline is known (CI, authoritative bucket build IDs available) and none of its builds produced
     * a measurement for this scenario - i.e. the bucket result was restored from the Gradle build cache instead of
     * being executed. Any status/failure carried by the result JSON was recorded by the original producing build,
     * not by this build chain, so the report must not present it as this chain's outcome.
     */
    final boolean fromCache

    String getName() {
        return "$scenarioName | $testProject | ${scenarioClass.substring(scenarioClass.lastIndexOf(".") + 1)}"
    }

    String getScenarioName() {
        return performanceExperiment.scenario.testName
    }

    String getScenarioClass() {
        return performanceExperiment.scenario.className
    }

    String getTestProject() {
        return performanceExperiment.testProject
    }

    boolean isCrossVersion() {
        return !crossBuild
    }

    boolean isUnknown() {
        return teamCityExecutions.any { it.isUnknown() }
    }

    boolean isFlaky() {
        return teamCityExecutions.size() > 1 && teamCityExecutions.count { it.successful } == 1
    }

    boolean isImproved() {
        return !crossBuild && !currentExecutions.empty && currentExecutions.every { it.confidentToSayBetter() }
    }

    /**
     * Whether this pipeline's own measurements (from the DB, not the possibly-stale result JSON status) show a
     * regression by the same criterion the performance test itself fails on. This is the signal used to fail the
     * build. Deliberately NOT {@code confidentToSayWorse()}: that is the weak >90%-confidence NEARLY-FAILED display
     * heuristic, and gating on it fails scenarios whose own test assertion passed.
     *
     * Requires *every* current execution to regress, not just any: a flaky scenario is retried in-pipeline
     * ({@code testRetry.maxRetries = 1} on CI), so both the failing attempt and its passing retry are recorded as
     * current executions here. Gating on {@code any} would re-fail a scenario that regressed once and recovered on
     * retry - exactly the flakiness the test-retry mechanism is meant to tolerate. Only a regression that holds
     * across all of this pipeline's measurements fails the build.
     */
    boolean isRegressedByMeasurement() {
        return !crossBuild && !currentExecutions.empty && currentExecutions.every { it.regressedSignificantly() }
    }

    boolean isBuildFailed() {
        return teamCityExecutions.every { it.isBuildFailed() } && currentExecutions.empty
    }

    boolean isRegressed() {
        return teamCityExecutions.every { it.isBuildFailed() } && !currentExecutions.empty
    }

    boolean isSuccessful() {
        return teamCityExecutions.every { it.isSuccessful() }
    }

    boolean isAboutToRegress() {
        return !crossBuild && currentExecutions.any { it.confidentToSayWorse() }
    }

    double getDifferenceSortKey() {
        if (currentExecutions.empty) {
            return Double.NEGATIVE_INFINITY
        }
        def firstExecution = currentExecutions[0]
        double signum = Math.signum(firstExecution.differencePercentage)
        if (signum == 0.0d) {
            signum = -1.0
        }
        return firstExecution.confidencePercentage * signum
    }

    double getDifferencePercentage() {
        return currentExecutions.empty ? Double.NEGATIVE_INFINITY : currentExecutions[0].getDifferencePercentage()
    }
}
