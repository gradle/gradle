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
import com.fasterxml.jackson.databind.ObjectMapper

@JsonIgnoreProperties(ignoreUnknown = true)
class ScenarioBuildResultData {
    String scenarioName
    String webUrl
    boolean successful
    List<ExperimentData> experimentData = []

    static class ExperimentData {
        String buildId
        String gitCommitId
        String controlGroupName
        String experimentGroupName
        String controlGroupMedian
        String experimentGroupMedian
        String controlGroupStandardError
        String experimentGroupStandardError
        String confidence
        double regressionPercentage
    }

    String getGitCommitId(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].gitCommitId : "N/A"
    }

    String getBuildId(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].buildId : "N/A"
    }

    String getControlGroupName(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].controlGroupName : "N/A"
    }

    String getExperimentGroupName(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].experimentGroupName : "N/A"
    }

    String getControlGroupMedian(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].controlGroupMedian : "N/A"
    }

    String getExperimentGroupMedian(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].experimentGroupMedian : "N/A"
    }

    String getControlGroupStandardError(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].controlGroupStandardError : "N/A"
    }

    String getExperimentGroupStandardError(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].experimentGroupStandardError : "N/A"
    }

    String getConfidence(int experimentIndex) {
        return experimentData.size() > experimentIndex ? experimentData[experimentIndex].confidence : "N/A"
    }

    String getFormattedRegression(int experimentIndex) {
        return experimentData.size() > experimentIndex ? String.format("%.2f%%", experimentData[experimentIndex].regressionPercentage) : "N/A"
    }

    double getRegressionPercentage() {
        return experimentData.empty ? 0.0 : experimentData[0].regressionPercentage
    }

    boolean isRegressed() {
        return !successful && !experimentData.empty
    }

    void setResult(String junitSystemOut) {
        if(!junitSystemOut) {
            return
        }

        List<String> lines = junitSystemOut.readLines()
        List<Integer> startAndEndIndices = lines.findIndexValues { it.startsWith(BaselineVersion.MACHINE_DATA_SEPARATOR) }
        if (!startAndEndIndices.empty) {
            assert startAndEndIndices.size() <= 6
            for (int i = 0; i < startAndEndIndices.size(); i += 2) {
                int startIndex = startAndEndIndices[i].intValue()
                int endIndex = startAndEndIndices[i + 1].intValue()
                assert startIndex + 2 == endIndex
                String json = lines[startIndex + 1]
                experimentData.add(new ObjectMapper().readValue(json, ExperimentData))
            }
        }
    }

}
