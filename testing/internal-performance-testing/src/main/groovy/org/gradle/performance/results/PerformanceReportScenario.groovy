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

    /**
     * The database rows this pipeline produced that recorded no measurement, i.e. runs that errored out.
     */
    final List<UnmeasuredExecution> currentUnmeasuredExecutions

    final boolean crossBuild

    PerformanceReportScenario(
        List<PerformanceTestExecutionResult> teamCityExecutions,
        List<PerformanceReportScenarioHistoryExecution> historyExecutions,
        List<UnmeasuredExecution> unmeasuredExecutions,
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
        Closure<Boolean> producedByThisPipeline = pipelineBuildIds.isEmpty()
            ? { it.commitId == currentCommit }
            : { pipelineBuildIds.contains(it.teamCityBuildId) }
        this.currentExecutions = historyExecutions.findAll(producedByThisPipeline)
        this.currentUnmeasuredExecutions = unmeasuredExecutions.findAll(producedByThisPipeline)
        this.historyExecutions = historyExecutions
        // Only a scenario this pipeline left no trace of at all - neither a measurement nor an errored run - can have
        // been restored from the cache. An errored run does write a row, so it must not be excused as a cache hit.
        this.fromCache = !pipelineBuildIds.isEmpty() && currentExecutions.empty && currentUnmeasuredExecutions.empty
    }

    /**
     * True when this pipeline is known (CI, authoritative bucket build IDs available) and none of its builds left any
     * record of this scenario - i.e. the bucket result was restored from the Gradle build cache instead of being
     * executed. Any status/failure carried by the result JSON was recorded by the original producing build, not by
     * this build chain, so the report must not present it as this chain's outcome.
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

    /**
     * Whether this pipeline ran the scenario and it failed without producing a measurement - it errored out (an
     * Android Studio sync that cannot start, a test project that no longer builds) rather than regressing.
     *
     * Such a run records no comparable measurement, so {@link #isRegressedByMeasurement()} cannot see it and nothing
     * else gates on it. The evidence is the execution row the runner writes from its {@code finally} block, stamped
     * with the id of the build that actually ran it: a build-cache hit forks no test JVM, so it can neither write nor
     * replay one, and a result restored from the cache is still correctly ignored.
     *
     * Requires that no in-pipeline run *did* measure, so a scenario that errors once and succeeds on the in-pipeline
     * retry ({@code testRetry.maxRetries = 1} on CI) stays green, consistent with {@link #isRegressedByMeasurement()}.
     */
    boolean isErroredInThisPipeline() {
        return !crossBuild && !currentUnmeasuredExecutions.empty && currentExecutions.empty
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
