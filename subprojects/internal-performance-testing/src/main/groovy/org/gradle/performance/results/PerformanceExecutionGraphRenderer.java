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

package org.gradle.performance.results;

import org.gradle.performance.util.Git;

import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public interface PerformanceExecutionGraphRenderer {
    default List<Graph> getGraphs(PerformanceTestHistory history) {
        List<PerformanceTestExecution> executions = history.getExecutions()
            .stream()
            .filter(this::sameCommit)
            .filter(this::hasTwoDataLines)
            .collect(toList());
        return IntStream.range(0, executions.size()).mapToObj(i -> toGraph(executions.get(i), i + 1)).collect(toList());
    }

    default boolean hasTwoDataLines(PerformanceTestExecution execution) {
        return execution.getScenarios().size() > 1;
    }

    default boolean hasData(MeasuredOperationList measuredOperations) {
        return !measuredOperations.getTotalTime().isEmpty();
    }

    default Graph toGraph(PerformanceTestExecution execution, int index) {
        String id = "execution_" + index;
        String title = "Execution " + index;

        Line baseline = new Line(execution.getScenarios().stream().filter(this::hasData).findFirst().orElse(new MeasuredOperationList()));
        Line current = new Line(execution.getScenarios().get(execution.getScenarios().size() - 1));

        return new Graph(id, title, baseline, current);
    }

    default boolean sameCommit(PerformanceTestExecution execution) {
        return execution.getVcsCommits().contains(Git.current().getCommitId());
    }
}
