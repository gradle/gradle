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
import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatisticsListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class TaskExecutionStatisticsReporter implements TaskExecutionStatisticsListener {
    private final StyledTextOutputFactory textOutputFactory;

    public TaskExecutionStatisticsReporter(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    public void buildFinished(final TaskExecutionStatistics statistics) {
        final int total = statistics.getAvoidedTasksCount() + statistics.getExecutedTasksCount();
        if (total > 0) {
            final long avoidedPercentage = Math.round(statistics.getAvoidedTasksCount() * 100.0 / total);
            final String pluralizedTasks = total > 1 ? "tasks" : "task";
            StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, LogLevel.LIFECYCLE);
            textOutput.formatln("%d actionable %s: %d executed, %d avoided (%d%%)", total, pluralizedTasks, statistics.getExecutedTasksCount(), statistics.getAvoidedTasksCount(), avoidedPercentage);
        }
    }
}
