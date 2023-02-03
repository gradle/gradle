/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.buildevents;

import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatistics;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class TaskExecutionStatisticsReporter {
    private final StyledTextOutputFactory textOutputFactory;

    public TaskExecutionStatisticsReporter(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    public void buildFinished(TaskExecutionStatistics statistics) {
        int total = statistics.getTotalTaskCount();
        if (total > 0) {
            String pluralizedTasks = total > 1 ? "tasks" : "task";
            StyledTextOutput textOutput = textOutputFactory.create(TaskExecutionStatisticsReporter.class, LogLevel.LIFECYCLE);
            textOutput.format("%d actionable %s:", total, pluralizedTasks);
            boolean printedDetail = formatDetail(textOutput, statistics.getExecutedTasksCount(), "executed", false);
            printedDetail = formatDetail(textOutput, statistics.getFromCacheTaskCount(), "from cache", printedDetail);
            formatDetail(textOutput, statistics.getUpToDateTaskCount(), "up-to-date", printedDetail);
            textOutput.println();
        }
    }

    private static boolean formatDetail(StyledTextOutput textOutput, int count, String title, boolean alreadyPrintedDetail) {
        if (count == 0) {
            return alreadyPrintedDetail;
        }
        if (alreadyPrintedDetail) {
            textOutput.format(",");
        }
        textOutput.format(" %d %s", count, title);
        return true;
    }
}
