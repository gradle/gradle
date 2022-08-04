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

import org.gradle.performance.measure.DataSeries

import java.time.Instant

/**
 * Each instance represents a performance execution, i.e. a row in performance execution database
 */
class PerformanceReportScenarioHistoryExecution {
    private static final int ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD = 90
    Instant time
    String teamCityBuildId
    String commitId
    String shortCommitId
    MeasuredOperationList baseVersion
    MeasuredOperationList currentVersion

    PerformanceReportScenarioHistoryExecution(long time, String teamCityBuildId, String commitId, MeasuredOperationList baseVersion, MeasuredOperationList currentVersion) {
        this.time = Instant.ofEpochMilli(time)
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
