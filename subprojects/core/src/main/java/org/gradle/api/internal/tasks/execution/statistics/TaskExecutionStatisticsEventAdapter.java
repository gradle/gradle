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
package org.gradle.api.internal.tasks.execution.statistics;

import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;

public class TaskExecutionStatisticsEventAdapter extends BuildAdapter implements BuildListener, TaskExecutionListener {
    private final TaskExecutionStatisticsListener listener;
    private int executedTasksCount;
    private int fromCacheTaskCount;
    private int upToDateTaskCount;

    public TaskExecutionStatisticsEventAdapter(TaskExecutionStatisticsListener listener) {
        this.listener = listener;
    }

    @Override
    public void buildFinished(BuildResult result) {
        // Do not report stats for nested builds
        if (result.getGradle().getParent() == null) {
            listener.buildFinished(new TaskExecutionStatistics(executedTasksCount, fromCacheTaskCount, upToDateTaskCount));
        }
    }

    @Override
    public void beforeExecute(Task task) {
        // do nothing
    }

    @Override
    public void afterExecute(Task task, TaskState state) {
        if (!taskIsForNestedBuild(task)) {
            TaskStateInternal stateInternal = (TaskStateInternal) state;
            if (stateInternal.isActionable()) {
                switch (stateInternal.getOutcome()) {
                    case EXECUTED:
                        executedTasksCount++;
                        break;
                    case FROM_CACHE:
                        fromCacheTaskCount++;
                        break;
                    case UP_TO_DATE:
                        upToDateTaskCount++;
                        break;
                    default:
                        // Ignore any other outcome
                        break;
                }
            }
        }
    }

    private boolean taskIsForNestedBuild(Task task) {
        return task.getProject().getGradle().getParent() != null;
    }
}
