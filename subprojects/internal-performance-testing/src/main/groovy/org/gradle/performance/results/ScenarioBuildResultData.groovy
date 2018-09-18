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
    String scenarioName
    String webUrl
    String testFailure
    boolean successful
    List<ExecutionData> currentCommitExecutions = []
    List<ExecutionData> recentExecutions = []

    boolean isAboutToRegress() {
        return executions.any { it.regressionPercentage > 0 && it.confidencePercentage > IndexPageGenerator.DANGEROUS_REGRESSION_CONFIDENCE_THRESHOLD }
    }

    boolean isBuildFailed() {
        return !successful && currentCommitExecutions.empty
    }

    boolean isFromCache() {
        return successful && currentCommitExecutions.empty
    }

    double getRegressionSortKey() {
        return executions.empty ? Double.NEGATIVE_INFINITY : executions[0].confidencePercentage * Math.signum(executions[0].regressionPercentage)
    }

    List<ExecutionData> getExecutions() {
        return currentCommitExecutions.isEmpty() ? recentExecutions : currentCommitExecutions
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

        double getRegressionPercentage() {
            double base = baseVersion.totalTime.median.value.doubleValue()
            double current = currentVersion.totalTime.median.value.doubleValue()
            return 100.0 * (current - base) / base
        }

        double getConfidencePercentage() {
            return 100.0 * DataSeries.confidenceInDifference(baseVersion.totalTime, currentVersion.totalTime)
        }

        String getFormattedRegression() {
            String.format("%.2f%%", regressionPercentage)
        }

        String getFormattedConfidence() {
            String.format("%.1f%%", confidencePercentage)
        }
    }
}
