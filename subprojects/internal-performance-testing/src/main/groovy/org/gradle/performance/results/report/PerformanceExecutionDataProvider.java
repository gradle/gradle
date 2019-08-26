/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ScenarioBuildResultData;
import org.gradle.performance.util.Git;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collector;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

public abstract class PerformanceExecutionDataProvider {
    protected static final int PERFORMANCE_DATE_RETRIEVE_DAYS = 2;
    protected TreeSet<ScenarioBuildResultData> scenarioExecutionData;
    protected final ResultsStore resultsStore;
    private final File resultsJson;
    protected final String commitId = Git.current().getCommitId();

    public PerformanceExecutionDataProvider(ResultsStore resultsStore, File resultsJson) {
        this.resultsJson = resultsJson;
        this.resultsStore = resultsStore;
    }

    public TreeSet<ScenarioBuildResultData> getScenarioExecutionData() {
        if (scenarioExecutionData == null) {
            scenarioExecutionData = readResultJsonAndQueryFromDatabase(resultsJson);
        }

        return scenarioExecutionData;
    }

    public String getCommitId() {
        return commitId;
    }

    protected abstract TreeSet<ScenarioBuildResultData> queryExecutionData(List<ScenarioBuildResultData> scenarioList);

    protected TreeSet<ScenarioBuildResultData> readResultJsonAndQueryFromDatabase(File resultJson) {
        try {
            // @formatter:off
            List<ScenarioBuildResultData> list = new ObjectMapper().readValue(resultJson, new TypeReference<List<ScenarioBuildResultData>>() { });
            // @formatter:on
            return queryExecutionData(list);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected Collector<ScenarioBuildResultData, ?, TreeSet<ScenarioBuildResultData>> treeSetCollector(Comparator<ScenarioBuildResultData> scenarioComparator) {
        return toCollection(() -> new TreeSet<>(scenarioComparator));
    }

    protected boolean sameCommit(ScenarioBuildResultData.ExecutionData execution) {
        return commitId.equals(execution.getCommitId());
    }


    protected List<ScenarioBuildResultData.ExecutionData> removeEmptyExecution(List<? extends PerformanceTestExecution> executions) {
        return executions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(toList());
    }

    private ScenarioBuildResultData.ExecutionData extractExecutionData(PerformanceTestExecution performanceTestExecution) {
        List<MeasuredOperationList> nonEmptyExecutions = performanceTestExecution
            .getScenarios()
            .stream()
            .filter(testExecution -> !testExecution.getTotalTime().isEmpty())
            .collect(toList());
        if (nonEmptyExecutions.size() > 1) {
            int size = nonEmptyExecutions.size();
            return new ScenarioBuildResultData.ExecutionData(performanceTestExecution.getStartTime(), getCommit(performanceTestExecution), nonEmptyExecutions.get(size - 2), nonEmptyExecutions.get(size - 1));
        } else {
            return null;
        }
    }


    private String getCommit(PerformanceTestExecution execution) {
        if (execution.getVcsCommits().isEmpty()) {
            return "";
        } else {
            return execution.getVcsCommits().get(0);
        }
    }

}
