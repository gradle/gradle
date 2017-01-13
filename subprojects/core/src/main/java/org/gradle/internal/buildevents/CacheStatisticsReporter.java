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
import org.gradle.api.logging.LogLevel;
import org.gradle.caching.internal.tasks.statistics.TaskExecutionStatistics;
import org.gradle.caching.internal.tasks.statistics.TaskExecutionStatisticsListener;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class CacheStatisticsReporter implements TaskExecutionStatisticsListener {
    private final StyledTextOutputFactory textOutputFactory;

    public CacheStatisticsReporter(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public void buildFinished(TaskExecutionStatistics statistics) {
        StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, LogLevel.LIFECYCLE);
        textOutput.println();
        int allTasks = statistics.getAllTasksCount();
        int allExecutedTasks = statistics.getTasksCount(TaskExecutionOutcome.EXECUTED);
        int skippedTasks = statistics.getTasksCount(TaskExecutionOutcome.SKIPPED);
        int upToDateTasks = statistics.getTasksCount(TaskExecutionOutcome.UP_TO_DATE);
        int noSourceTasks = statistics.getTasksCount(TaskExecutionOutcome.NO_SOURCE);
        int fromCacheTasks = statistics.getTasksCount(TaskExecutionOutcome.FROM_CACHE);
        int cacheableExecutedTasks = statistics.getCacheMissCount();
        int nonCacheableExecutedTasks = allExecutedTasks - cacheableExecutedTasks;
        textOutput.formatln("%d tasks in build, out of which %d (%d%%) were executed", allTasks, allExecutedTasks, roundedPercentOf(allExecutedTasks, allTasks));
        statisticsLine(textOutput, skippedTasks, allTasks, "skipped");
        statisticsLine(textOutput, upToDateTasks, allTasks, "up-to-date");
        statisticsLine(textOutput, noSourceTasks, allTasks, "no-source");
        statisticsLine(textOutput, fromCacheTasks, allTasks, "loaded from cache");
        statisticsLine(textOutput, cacheableExecutedTasks, allTasks, "cache miss");
        statisticsLine(textOutput, nonCacheableExecutedTasks, allTasks, "not cacheable");
    }

    private void statisticsLine(StyledTextOutput textOutput, int fraction, int total, String description) {
        if (fraction > 0) {
            int numberLength = Integer.toString(total).length();
            String percent = String.format("(%d%%)", roundedPercentOf(fraction, total));
            textOutput.formatln("%" + numberLength + "d %6s %s", fraction, percent, description);
        }
    }

    private static int roundedPercentOf(int fraction, int total) {
        return total == 0
            ? 0
            : (int) Math.round(100d * fraction / total);
    }
}
