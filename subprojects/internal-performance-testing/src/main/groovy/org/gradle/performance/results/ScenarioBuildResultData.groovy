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

@JsonIgnoreProperties(ignoreUnknown = true)
class ScenarioBuildResultData {
    public static final String STATUS_SUCCESS = "SUCCESS"
    public static final String STATUS_FAILURE = "FAILURE"
    public static final String STATUS_UNKNOWN = "UNKNOWN"
    public static final int FLAKINESS_DETECTION_THRESHOLD = 99
    private static final int ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD = 90
    String teamCityBuildId
    String scenarioName
    String scenarioClass
    String webUrl
    String agentName
    String agentUrl
    String testFailure
    String status
    boolean crossBuild
    List<ExecutionData> currentBuildExecutions = []
    List<ExecutionData> recentExecutions = []

    // For rerun scenarios
    List<ScenarioBuildResultData> rawData = []

    boolean isCrossVersion() {
        return !crossBuild
    }

    boolean isAboutToRegress() {
        return !crossBuild && executions.any { it.confidentToSayWorse() }
    }

    boolean isImproved() {
        return !crossBuild && executionsToDisplayInRow.every { it.confidentToSayBetter() }
    }

    boolean isUnknown() {
        return status == STATUS_UNKNOWN
    }

    boolean isSuccessful() {
        return status == STATUS_SUCCESS
    }

    boolean isBuildFailed() {
        return status == STATUS_FAILURE && currentBuildExecutions.empty
    }

    boolean isRegressed() {
        return status == STATUS_FAILURE && !currentBuildExecutions.empty
    }

    boolean isFromCache() {
        return status == STATUS_SUCCESS && currentBuildExecutions.empty
    }

    double getDifferenceSortKey() {
        if (executions.empty) {
            return Double.NEGATIVE_INFINITY
        }
        def firstExecution = executions[0]
        double signum = Math.signum(firstExecution.differencePercentage)
        if (signum == 0.0d) {
            signum = -1.0
        }
        return firstExecution.confidencePercentage * signum
    }

    double getDifferencePercentage() {
        return executions.empty ? Double.NEGATIVE_INFINITY : executions[0].getDifferencePercentage()
    }

    List<ExecutionData> getExecutions() {
        return currentBuildExecutions.isEmpty() ? recentExecutions : currentBuildExecutions
    }

    List<ExecutionData> getExecutionsToDisplayInRow() {
        if (fromCache) {
            return executions.subList(0, Math.min(1, executions.size()))
        } else {
            return executions
        }
    }

    boolean isFlaky() {
        return rawData.size() > 1 && rawData.count { it.successful } == 1;
    }

    static class ExecutionData {
        Date time
        String commitId
        String shortCommitId
        MeasuredOperationList baseVersion
        MeasuredOperationList currentVersion

        ExecutionData(long time, String commitId, MeasuredOperationList baseVersion, MeasuredOperationList currentVersion) {
            this.time = new Date(time)
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
}
