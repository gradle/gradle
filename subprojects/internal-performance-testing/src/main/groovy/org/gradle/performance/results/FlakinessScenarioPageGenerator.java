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

import com.google.common.collect.ImmutableMap;
import groovy.json.JsonOutput;
import org.gradle.performance.util.Git;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FlakinessScenarioPageGenerator extends HtmlPageGenerator<PerformanceTestHistory> {
    private final String commitId = Git.current().getCommitId();
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public int getDepth() {
        return 1;
    }

    @Override
    public void render(PerformanceTestHistory history, Writer writer) throws IOException {
        List<Graph> graphs = getGraphs(history);
        // @formatter:off
        new MetricsHtml(writer) {{
            html();
                head();
                    headSection(this);
                    title().text("Flaky report for "+ history.getDisplayName()).end();
                end();
                body();
                    h2().text("Flaky report for " + history.getDisplayName()).end();
                    div().id("flot-placeholder").end();
                    graphs.forEach(this::renderGraph);
                end();
            end();
        }

        private void renderGraph(Graph graph) {
            h3().text(graph.title).end();
            div().id(graph.id).classAttr("chart").end();
            script().raw(String.format("$.plot('#%s', %s, %s)", graph.id, graph.getData(), graph.getOptions())).end();
        }
        };
        // @formatter:on
    }

    private List<Graph> getGraphs(PerformanceTestHistory history) {
        return history.getExecutions()
            .stream()
            .filter(this::sameCommit)
            .filter(this::hasTwoDataLines)
            .map(this::toGraph)
            .collect(Collectors.toList());
    }

    private boolean hasTwoDataLines(PerformanceTestExecution execution) {
        return execution.getScenarios().size() > 1;
    }

    private boolean hasData(MeasuredOperationList measuredOperations) {
        return !measuredOperations.getTotalTime().isEmpty();
    }

    private Graph toGraph(PerformanceTestExecution execution) {
        int index = counter.incrementAndGet();
        String id = "execution_" + index;
        String title = "Execution " + index;

        Line baseline = new Line(execution.getScenarios().stream().filter(this::hasData).findFirst().orElse(new MeasuredOperationList()));
        Line current = new Line(execution.getScenarios().get(execution.getScenarios().size() - 1));

        return new Graph(id, title, baseline, current);
    }

    private boolean sameCommit(PerformanceTestExecution execution) {
        return execution.getVcsCommits().contains(commitId);
    }

    private static class Graph {
        String id;
        String title;
        List<Line> data;

        public Graph(String id, String title, Line... lines) {
            this.id = id;
            this.title = title;
            this.data = Arrays.asList(lines);
        }

        String getData() {
            return JsonOutput.toJson(data);
        }

        String getOptions() {
            List<List<Object>> ticks = IntStream.range(0, data.get(0).data.size())
                .mapToObj(index -> Arrays.<Object>asList(index, index))
                .collect(Collectors.toList());
            return JsonOutput.toJson(ImmutableMap.of("xaxis", ImmutableMap.of("ticks", ticks)));
        }
    }

    private static class Line {
        private static final Map<String, Object> SHOW_TRUE = ImmutableMap.of("show", true);
        private static final Map<String, Object> SHOW_FALSE = ImmutableMap.of("show", false);
        String label;
        List<List<Number>> data;

        public Line(MeasuredOperationList measuredOperations) {
            List<Double> points = measuredOperations.getTotalTime().asDoubleList();
            this.label = measuredOperations.getName();
            this.data = IntStream.range(0, points.size())
                .mapToObj(index -> Arrays.<Number>asList(index, points.get(index)))
                .collect(Collectors.toList());
        }

        public String getLabel() {
            return label;
        }

        public List<List<Number>> getData() {
            return data;
        }

        public boolean getStack() {
            return false;
        }

        public Map getBars() {
            return SHOW_FALSE;
        }

        public Map getLines() {
            return SHOW_TRUE;
        }

        public Map getPoints() {
            return SHOW_TRUE;
        }
    }
}
