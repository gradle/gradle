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
import org.gradle.internal.UncheckedException;
import org.gradle.performance.results.MeasuredOperationList;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.PerformanceReportScenarioHistoryExecution;
import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.PerformanceTestExecutionResult;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.UnmeasuredExecution;
import org.gradle.performance.util.Git;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

public abstract class PerformanceExecutionDataProvider {
    protected static final int PERFORMANCE_DATE_RETRIEVE_DAYS = 7;
    protected TreeSet<PerformanceReportScenario> scenarioExecutions;
    protected final ResultsStore resultsStore;
    protected final Set<String> performanceTestBuildIds;
    private final List<File> resultJsons;
    protected final String commitId = Git.current().getCommitId();

    public PerformanceExecutionDataProvider(ResultsStore resultsStore, List<File> resultJsons, Set<String> performanceTestBuildIds) {
        this.resultJsons = resultJsons;
        this.resultsStore = resultsStore;
        this.performanceTestBuildIds = performanceTestBuildIds;
    }

    public TreeSet<PerformanceReportScenario> getReportScenarios() {
        if (scenarioExecutions == null) {
            scenarioExecutions = readResultJsonAndQueryFromDatabase();
        }

        return scenarioExecutions;
    }

    public String getCommitId() {
        return commitId;
    }

    protected abstract TreeSet<PerformanceReportScenario> queryExecutionData(List<PerformanceTestExecutionResult> scenarioExecutions);

    private TreeSet<PerformanceReportScenario> readResultJsonAndQueryFromDatabase() {
        List<PerformanceTestExecutionResult> buildResultData = resultJsons.stream()
            .flatMap(PerformanceExecutionDataProvider::parseResultsJson)
            .collect(toList());
        return queryExecutionData(buildResultData);
    }

    private static Stream<PerformanceTestExecutionResult> parseResultsJson(File resultsJson) {
        try {
            return new ObjectMapper().readValue(resultsJson, new TypeReference<List<PerformanceTestExecutionResult>>() {
            }).stream();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    protected <T> Collector<T, ?, TreeSet<T>> treeSetCollector(Comparator<T> scenarioComparator) {
        return toCollection(() -> new TreeSet<>(scenarioComparator));
    }

    protected List<PerformanceReportScenarioHistoryExecution> removeEmptyExecution(List<? extends PerformanceTestExecution> executions) {
        return executions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(toList());
    }

    /**
     * The counterpart of {@link #removeEmptyExecution}: the rows it drops, kept as {@link UnmeasuredExecution}.
     *
     * A scenario that errors out before measuring anything still writes its execution row - the runner reports in a
     * {@code finally} block - so these rows are how the report learns that this pipeline ran a scenario and it failed
     * without producing a measurement. Discarding them silently is what let a broken scenario stay invisible to the
     * build gate.
     */
    protected List<UnmeasuredExecution> unmeasuredExecutions(List<? extends PerformanceTestExecution> executions) {
        return executions.stream()
            .filter(execution -> !hasComparableMeasurements(execution))
            .map(execution -> new UnmeasuredExecution(execution.getTeamCityBuildId(), getCommit(execution)))
            .collect(toList());
    }

    private PerformanceReportScenarioHistoryExecution extractExecutionData(PerformanceTestExecution performanceTestExecution) {
        List<MeasuredOperationList> nonEmptyExecutions = measuredScenarios(performanceTestExecution);
        if (nonEmptyExecutions.size() > 1) {
            int size = nonEmptyExecutions.size();
            return new PerformanceReportScenarioHistoryExecution(
                performanceTestExecution.getStartTime(),
                performanceTestExecution.getTeamCityBuildId(),
                getCommit(performanceTestExecution),
                nonEmptyExecutions.get(size - 2),
                nonEmptyExecutions.get(size - 1)
            );
        } else {
            return null;
        }
    }

    /**
     * Whether this row holds enough measurements to compare a baseline against the version under test. Fewer than two
     * non-empty series means no comparison is possible, which is the shape of an errored run.
     */
    private static boolean hasComparableMeasurements(PerformanceTestExecution performanceTestExecution) {
        return measuredScenarios(performanceTestExecution).size() > 1;
    }

    private static List<MeasuredOperationList> measuredScenarios(PerformanceTestExecution performanceTestExecution) {
        return performanceTestExecution
            .getScenarios()
            .stream()
            .filter(testExecution -> !testExecution.getTotalTime().isEmpty())
            .collect(toList());
    }

    private String getCommit(PerformanceTestExecution execution) {
        if (execution.getVcsCommits().isEmpty()) {
            return "";
        } else {
            return execution.getVcsCommits().get(0);
        }
    }
}
