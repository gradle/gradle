/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Lists;
import groovy.json.JsonGenerator;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestDataGenerator extends ReportRenderer<PerformanceTestHistory, Writer> {

    @Override
    public void render(PerformanceTestHistory testHistory, Writer output) throws IOException {
        PrintWriter out = new PrintWriter(output);
        List<? extends PerformanceTestExecution> sortedResults = Lists.reverse(testHistory.getExecutions());

        List<ExecutionLabel> executionLabels = sortedResults.stream().map(ExecutionLabel::new).collect(Collectors.toList());
        List<ExecutionData> totalTimeData = testHistory.getScenarioLabels()
            .stream()
            .map(label -> executionDataForLabel(testHistory, label, FormatSupport::getTotalTimeSeconds, Function.identity()))
            .collect(Collectors.toList());
        List<ExecutionData> confidenceData = extractBaselineData(testHistory, FormatSupport::getConfidencePercentage);
        List<ExecutionData> differenceData = extractBaselineData(testHistory, FormatSupport::getDifferencePercentage);

        String json = new JsonGenerator.Options().excludeNulls().build().toJson(new AllExecutionData(executionLabels, totalTimeData, confidenceData, differenceData));
        out.print(json);
        out.flush();
    }

    private List<ExecutionData> extractBaselineData(PerformanceTestHistory testHistory, DataExtractor dataExtractor) {
        if (testHistory instanceof CrossVersionPerformanceTestHistory) {
            return ((CrossVersionPerformanceTestHistory) testHistory).getBaselineVersions()
                .stream()
                .map(label -> executionDataForLabel(testHistory, label, dataExtractor, mainVsBaselineLabelFormatter(testHistory)))
                .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    private ExecutionData executionDataForLabel(PerformanceTestHistory testHistory, String label, DataExtractor dataExtractor, Function<String, String> labelFormatter) {
        List<? extends PerformanceTestExecution> sortedExecutions = Lists.reverse(testHistory.getExecutions());
        List<List<Number>> data = IntStream.range(0, sortedExecutions.size()).mapToObj(index -> {
            PerformanceTestExecution results = sortedExecutions.get(index);
            MeasuredOperationList baselineVersion = results.getScenarios().get(testHistory.getScenarioLabels().indexOf(label));
            MeasuredOperationList currentVersion = results.getScenarios().get(testHistory.getScenarioLabels().size() - 1);
            return baselineVersion.isEmpty() ? null : Arrays.asList(index, dataExtractor.apply(baselineVersion, currentVersion));
        }).filter(Objects::nonNull).collect(Collectors.toList());
        return new ExecutionData(labelFormatter.apply(label), data);
    }

    private Function<String, String> mainVsBaselineLabelFormatter(PerformanceTestHistory testHistory) {
        return label -> {
            String mainLabel = testHistory.getScenarioLabels().get(testHistory.getScenarioLabels().size() - 1);
            return mainLabel + " vs " + label;
        };
    }

    private interface DataExtractor extends BiFunction<MeasuredOperationList, MeasuredOperationList, Number> {
        @Override
        Number apply(MeasuredOperationList baseline, MeasuredOperationList current);
    }

    static class AllExecutionData {
        private List<ExecutionLabel> executionLabels;
        private List<ExecutionData> totalTime;
        private List<ExecutionData> confidence;
        private List<ExecutionData> difference;

        AllExecutionData(List<ExecutionLabel> executionLabels, List<ExecutionData> totalTime, List<ExecutionData> confidence, List<ExecutionData> difference) {
            this.executionLabels = executionLabels;
            this.totalTime = totalTime;
            this.confidence = confidence;
            this.difference = difference;
        }

        public List<ExecutionLabel> getExecutionLabels() {
            return executionLabels;
        }

        public List<ExecutionData> getTotalTime() {
            return totalTime;
        }

        public List<ExecutionData> getConfidence() {
            return confidence;
        }

        public List<ExecutionData> getDifference() {
            return difference;
        }
    }

    static class ExecutionData {
        private String label;
        private List<List<Number>> data;

        private ExecutionData(String label, List<List<Number>> data) {
            this.label = label;
            this.data = data;
        }

        public String getLabel() {
            return label;
        }

        public List<List<Number>> getData() {
            return data;
        }
    }

    class ExecutionLabel {
        private String id;
        private String branch;
        private String date;
        private List<String> commits;

        private ExecutionLabel(PerformanceTestExecution execution) {
            this.id = execution.getExecutionId();
            this.branch = execution.getVcsBranch();
            this.date = FormatSupport.date(new Date(execution.getStartTime()));
            this.commits = execution.getVcsCommits();
        }

        public String getId() {
            return id;
        }

        public String getBranch() {
            return branch;
        }

        public String getDate() {
            return date;
        }

        public List<String> getCommits() {
            return commits;
        }
    }
}
