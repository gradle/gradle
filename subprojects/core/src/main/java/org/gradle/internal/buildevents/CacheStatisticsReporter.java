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

import org.gradle.api.internal.tasks.cache.diagnostics.TaskExecutionStatistics;
import org.gradle.api.internal.tasks.cache.diagnostics.TaskExecutionStatisticsListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import java.text.DecimalFormat;

public class CacheStatisticsReporter implements TaskExecutionStatisticsListener {
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("###");

    private final StyledTextOutputFactory textOutputFactory;

    public CacheStatisticsReporter(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public void buildFinished(TaskExecutionStatistics statistics) {
        StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, LogLevel.LIFECYCLE);
        int cacheableTasks = statistics.getCacheableTasksCount();
        textOutput.println();
        textOutput.println("You are using the task output cache");
        if (cacheableTasks == 0) {
            textOutput.formatln("No executed tasks were cacheable - the task output cache has no effect");
        } else {
            int cachedTasks = statistics.getCachedTasksCount();
            int allTasks = statistics.getAllTasksCount();
            int upToDateTasks = statistics.getUpToDateTasksCount();
            textOutput.formatln("%d tasks in build, out of which %d (%s%%) were cacheable", allTasks, cacheableTasks, roundedPercentOf(cacheableTasks, allTasks));
            statisticsLine(textOutput, upToDateTasks, allTasks, "up-to-date");
            statisticsLine(textOutput, cachedTasks, allTasks, "loaded from cache");
            statisticsLine(textOutput, statistics.getSkippedTasksCount(), allTasks, "skipped");
            statisticsLine(textOutput, statistics.getExecutedTasksCount(), allTasks, "executed");
        }
    }

    private void statisticsLine(StyledTextOutput textOutput, int fraction, int total, String description) {
        if (fraction > 0) {
            int numberLength = Integer.toString(total).length();
            String percent = String.format("(%s%%)", roundedPercentOf(fraction, total));
            textOutput.formatln("%" + numberLength + "d " + "%6s %s", fraction, percent, description);
        }
    }

    private static String roundedPercentOf(long fraction, long total) {
        if (total < 0 || fraction < 0) {
            throw new IllegalArgumentException("Unable to calculate percentage: " + fraction + " of " + total
                + ". All inputs must be >= 0");
        }
        float out = (total == 0) ? 0 : fraction * 100.0f / total;
        return PERCENT_FORMAT.format(out);
    }

}
