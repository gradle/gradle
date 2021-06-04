/*
 * Copyright 2018 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.gradle.performance.measure.DataSeries


/**
 * Each instance represents a performance execution, i.e. a row in performance execution database
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class PerformanceReportScenarioTeamCityExecution {
    public static final String STATUS_SUCCESS = "SUCCESS"
    public static final String STATUS_FAILURE = "FAILURE"
    public static final String STATUS_UNKNOWN = "UNKNOWN"
    public static final int FLAKINESS_DETECTION_THRESHOLD = 99
    String teamCityBuildId
    String scenarioName
    String scenarioClass
    String testProject
    String webUrl
    String testFailure
    String status
    String commitId

    boolean isBuildFailed() {
        return status == STATUS_FAILURE
    }

    boolean isUnknown() {
        return status == STATUS_UNKNOWN
    }

    boolean isSuccessful() {
        return status == STATUS_SUCCESS
    }

    PerformanceExperiment getPerformanceExperiment() {
        new PerformanceExperiment(getTestProject(), new PerformanceScenario(getScenarioClass(), getScenarioName()))
    }
}

class PerformanceReportScenarioHistoryExecution {
    private static final int ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD = 90
    Date time
    String teamCityBuildId
    String commitId
    String shortCommitId
    MeasuredOperationList baseVersion
    MeasuredOperationList currentVersion

    PerformanceReportScenarioHistoryExecution(long time, String teamCityBuildId, String commitId, MeasuredOperationList baseVersion, MeasuredOperationList currentVersion) {
        this.time = new Date(time)
        this.teamCityBuildId = teamCityBuildId
        this.commitId = commitId
        this.shortCommitId = commitId.substring(0, Math.min(7, commitId.length()))
        this.baseVersion = baseVersion
        this.currentVersion = currentVersion
    }

    String getDifferenceDisplay() {
        return FormatSupport.getFormattedDifference(baseVersion.totalTime, currentVersion.totalTime)
    }

    double getDifferencePercentage() {
        return FormatSupport.getDifferencePercentage(baseVersion, currentVersion).doubleValue()
    }

    double getConfidencePercentage() {
        return 100.0 * DataSeries.confidenceInDifference(baseVersion.totalTime, currentVersion.totalTime)
    }

    String getFormattedDifferencePercentage() {
        String.format("%.2f%%", differencePercentage)
    }

    String getFormattedConfidence() {
        String.format("%.1f%%", confidencePercentage)
    }

    boolean confidentToSayBetter() {
        return differencePercentage <= 0 && confidencePercentage > ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD
    }

    boolean confidentToSayWorse() {
        return differencePercentage > 0 && confidencePercentage > ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD
    }
}
