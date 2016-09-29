/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.cache.statistics.TaskExecutionStatistics;
import org.gradle.api.internal.tasks.cache.statistics.TaskExecutionStatisticsListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import java.text.NumberFormat;

public class CacheStatisticsReporter implements TaskExecutionStatisticsListener {
    private final StyledTextOutputFactory textOutputFactory;

    public CacheStatisticsReporter(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public void buildFinished(TaskExecutionStatistics statistics) {
        StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, LogLevel.LIFECYCLE);
        int cacheableTasks = statistics.getCacheableTasksCount();
        textOutput.println();
        int allTasks = statistics.getAllTasksCount();
        int fromCacheTasks = statistics.getTasksCount(TaskExecutionOutcome.FROM_CACHE);
        int upToDateTasks = statistics.getTasksCount(TaskExecutionOutcome.UP_TO_DATE);
        textOutput.formatln("%d tasks in build, out of which %d (%s) were cacheable", allTasks, cacheableTasks, roundedPercentOf(cacheableTasks, allTasks));
        statisticsLine(textOutput, upToDateTasks, allTasks, "up-to-date");
        statisticsLine(textOutput, fromCacheTasks, allTasks, "loaded from cache");
        statisticsLine(textOutput, statistics.getTasksCount(TaskExecutionOutcome.SKIPPED), allTasks, "skipped");
        statisticsLine(textOutput, statistics.getTasksCount(TaskExecutionOutcome.EXECUTED), allTasks, "executed");
    }

    private void statisticsLine(StyledTextOutput textOutput, int fraction, int total, String description) {
        if (fraction > 0) {
            int numberLength = Integer.toString(total).length();
            String percent = String.format("(%s)", roundedPercentOf(fraction, total));
            textOutput.formatln("%" + numberLength + "d %6s %s", fraction, percent, description);
        }
    }

    private static String roundedPercentOf(float fraction, int total) {
        float out = (total == 0) ? 0 : fraction / total;
        // This uses RoundingMode.HALF_UP by default
        return NumberFormat.getPercentInstance().format(out);
    }
}
