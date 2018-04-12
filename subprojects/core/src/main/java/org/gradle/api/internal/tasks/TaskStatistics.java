/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.Maps;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.IoActions;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskStatistics implements Closeable {
    private final static Logger LOGGER = Logging.getLogger(TaskStatistics.class);
    private final static String TASK_STATISTICS_PROPERTY = "org.gradle.internal.tasks.stats";

    private final AtomicInteger eagerTasks = new AtomicInteger();
    private final AtomicInteger lazyTasks = new AtomicInteger();
    private final AtomicInteger lazyRealizedTasks = new AtomicInteger();
    private final Map<Class, Integer> typeCounts = Maps.newHashMap();
    private final boolean collectStatistics;

    private PrintWriter lazyTaskLog;

    public TaskStatistics() {
        String taskStatistics = System.getProperty(TASK_STATISTICS_PROPERTY);
        if (taskStatistics!=null) {
            collectStatistics = true;
            if (!taskStatistics.isEmpty()) {
                try {
                    lazyTaskLog = new PrintWriter(new FileWriter(taskStatistics));
                } catch (IOException e) {
                    // don't care
                }
            }
        } else {
            collectStatistics = false;
        }
    }

    public void eagerTask(Class<?> type) {
        if (collectStatistics) {
            eagerTasks.incrementAndGet();
            synchronized (typeCounts) {
                Integer count = typeCounts.get(type);
                if (count == null) {
                    count = 1;
                } else {
                    count = count + 1;
                }
                typeCounts.put(type, count);
            }
        }
    }

    public void lazyTask() {
        if (collectStatistics) {
            lazyTasks.incrementAndGet();
        }
    }

    public void lazyTaskRealized() {
        if (collectStatistics) {
            lazyRealizedTasks.incrementAndGet();
            if (lazyTaskLog != null) {
                new Throwable().printStackTrace(lazyTaskLog);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (collectStatistics) {
            LOGGER.lifecycle("E {} L {} LR {}", eagerTasks.getAndSet(0), lazyTasks.getAndSet(0), lazyRealizedTasks.getAndSet(0));
            for (Map.Entry<Class, Integer> typeCount : typeCounts.entrySet()) {
                LOGGER.lifecycle(typeCount.getKey() + " " + typeCount.getValue());
            }
            IoActions.closeQuietly(lazyTaskLog);
        }
    }
}
