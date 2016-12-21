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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.performance.measure.Amount;
import org.gradle.performance.measure.Duration;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Date;
import java.util.List;

public class TestDataGenerator extends ReportRenderer<PerformanceTestHistory, Writer> {
    protected final FormatSupport format = new FormatSupport();

    @Override
    public void render(PerformanceTestHistory testHistory, Writer output) throws IOException {
        PrintWriter out = new PrintWriter(output);
        List<? extends PerformanceTestExecution> sortedResults = Lists.reverse(testHistory.getExecutions());
        out.println("{");
        out.println("\"executionLabels\": [");
        for (int i = 0; i < sortedResults.size(); i++) {
            PerformanceTestExecution results = sortedResults.get(i);
            if (i > 0) {
                out.println(", ");
            }
            out.print("{");
            out.print("\"id\": \"" + results.getExecutionId() + "\"");
            out.print(", \"branch\":\"" + results.getVcsBranch() + "\"");
            out.print(", \"date\":\"" + format.date(new Date(results.getStartTime())) + "\"");
            out.print(", \"commits\":[\"");
            out.print(Joiner.on("\",\"").join(results.getVcsCommits()));
            out.print("\"]");
            out.print("}");
        }
        out.println("],");
        out.print("\"totalTime\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                return format.seconds(original.getTotalTime().getMedian());
            }
        }, out);
        out.println(",");
        out.print("\"configurationTime\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                return format.seconds(original.getConfigurationTime().getMedian());
            }
        }, out);
        out.println(",");
        out.print("\"executionTime\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                return format.seconds(original.getExecutionTime().getMedian());
            }
        }, out);
        out.println(",");
        out.print("\"compileTotalTime\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                return format.seconds(original.getCompileTotalTime().getMedian());
            }
        }, out);
        out.println(",");
        out.print("\"gcTotalTime\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                return format.seconds(original.getGcTotalTime().getMedian());
            }
        }, out);
        out.println(",");
        out.print("\"miscTime\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                Amount<Duration> miscTime = original.getTotalTime().getMedian()
                    .minus(original.getConfigurationTime().getMedian())
                    .minus(original.getExecutionTime().getMedian());
                return format.seconds(miscTime);
            }
        }, out);
        out.println(",");
        out.print("\"heapUsage\":");
        render(testHistory, new Transformer<String, MeasuredOperationList>() {
            public String transform(MeasuredOperationList original) {
                return format.megabytes(original.getTotalMemoryUsed().getMedian());
            }
        }, out);
        out.println("}");
        out.flush();
    }

    void render(PerformanceTestHistory testHistory, Transformer<String, MeasuredOperationList> valueRenderer, PrintWriter out) {
        List<? extends PerformanceTestExecution> sortedResults = Lists.reverse(testHistory.getExecutions());
        out.println("  [");
        List<String> labels = testHistory.getScenarioLabels();
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) {
                out.println(",");
            }
            out.println("  {");
            out.println("    \"label\": \"" + labels.get(i) + "\",");
            out.print("\"data\": [");
            boolean empty = true;
            for (int j = 0; j < sortedResults.size(); j++) {
                PerformanceTestExecution results = sortedResults.get(j);
                MeasuredOperationList measuredOperations = results.getScenarios().get(i);
                if (!measuredOperations.isEmpty()) {
                    if (!empty) {
                        out.print(", ");
                    }
                    out.print("[" + j + ", " + valueRenderer.transform(measuredOperations) + "]");
                    empty = false;
                }
            }
            out.println("]");
            out.print("  }");
        }
        out.println();
        out.println("]");
    }
}
