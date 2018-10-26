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
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataSeries

@JsonIgnoreProperties(ignoreUnknown = true)
class ScenarioBuildResultData {
    String teamCityBuildId
    String scenarioName
    String webUrl
    String testFailure
    String status
    boolean crossBuild
    List<ExecutionData> currentBuildExecutions = []
    List<ExecutionData> recentExecutions = []

    boolean isAboutToRegress() {
        return !crossBuild && executions.any { it.confidentToSayWorse() }
    }

    boolean isImproved() {
        return !crossBuild && executionsToDisplayInRow.every { it.confidentToSayBetter() }
    }

    boolean isUnknown() {
        return status == 'UNKNOWN'
    }

    boolean isSuccessful() {
        return status == 'SUCCESS'
    }

    boolean isBuildFailed() {
        return status == 'FAILURE'  && currentBuildExecutions.empty
    }

    boolean isRegressed() {
        return status == 'FAILURE' && !currentBuildExecutions.empty
    }

    boolean isFromCache() {
        return status == 'SUCCESS' && currentBuildExecutions.empty
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

    static class ExecutionData {
        Date time
        String commitId
        MeasuredOperationList baseVersion
        MeasuredOperationList currentVersion

        ExecutionData(long time, String commitId, MeasuredOperationList baseVersion, MeasuredOperationList currentVersion) {
            this.time = new Date(time)
            this.commitId = commitId
            this.baseVersion = baseVersion
            this.currentVersion = currentVersion
        }

        String getDifferenceDisplay() {
            Amount base = baseVersion.totalTime.median
            Amount current = currentVersion.totalTime.median
            Amount diff = current - base

            return String.format("%s (%s)", diff.format(), formattedDifferencePercentage)
        }

        double getDifferencePercentage() {
            double base = baseVersion.totalTime.median.value.doubleValue()
            double current = currentVersion.totalTime.median.value.doubleValue()
            return 100.0 * (current - base) / base
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
            return differencePercentage <= 0 && confidencePercentage > IndexPageGenerator.ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD
        }

        boolean confidentToSayWorse() {
            return differencePercentage > 0 && confidencePercentage > IndexPageGenerator.ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD
        }
    }
}
