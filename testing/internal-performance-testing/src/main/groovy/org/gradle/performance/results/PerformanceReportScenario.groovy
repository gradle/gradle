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

    final boolean fromCache

    PerformanceReportScenario(
        List<PerformanceTestExecutionResult> teamCityExecutions,
        List<PerformanceReportScenarioHistoryExecution> historyExecutions,
        boolean crossBuild,
        boolean fromCache
    ) {
        if (teamCityExecutions.empty) {
            throw new IllegalArgumentException("teamCity executions must not be empty!")
        }
        this.performanceExperiment = teamCityExecutions[0].performanceExperiment
        this.teamCityExecutions = teamCityExecutions
        this.crossBuild = crossBuild
        this.fromCache = fromCache

        Set<String> teamCityBuildIds = teamCityExecutions.collect { it.teamCityBuildId }.toSet()
        this.currentExecutions = historyExecutions.findAll {
            teamCityBuildIds.contains(it.teamCityBuildId)
        }
        this.historyExecutions = historyExecutions
    }

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
        return !crossBuild && currentExecutions.every { it.confidentToSayBetter() }
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
