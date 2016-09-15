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

import static org.gradle.internal.util.NumberUtil.percentOf;

public class CacheStatisticsReporter implements TaskExecutionStatisticsListener {
    private final StyledTextOutputFactory textOutputFactory;

    public CacheStatisticsReporter(StyledTextOutputFactory textOutputFactory) {
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public void buildFinished(TaskExecutionStatistics diagnostics) {
        StyledTextOutput textOutput = textOutputFactory.create(BuildResultLogger.class, LogLevel.LIFECYCLE);
        int cacheableTasks = diagnostics.getCacheableTasksCount();
        int executedTasks = diagnostics.getExecutedTasksCount();
        int cachedTasks = diagnostics.getCachedTasksCount();
        textOutput.println();
        textOutput.println("You are using the task output cache!");
        if (executedTasks == 0) {
            textOutput.formatln("No executed tasks were cacheable - the task output cache has no effect");
        } else {
            textOutput.formatln("%d%% executed tasks were cacheable (%d out of %d) ", percentOf(cacheableTasks, executedTasks), cacheableTasks, executedTasks);
            textOutput.formatln("%d%% cacheable tasks have been found in the cache (%d out of %d)", percentOf(cachedTasks, cacheableTasks), cachedTasks, cacheableTasks);
        }
    }
}
