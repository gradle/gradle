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

package org.gradle.api.internal.changedetection.changes;

import org.gradle.StartParameter;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.project.taskfactory.AbstractIncrementalTaskAction;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.specs.AndSpec;

@NonNullApi
public class DefaultTaskExecutionModeResolver implements TaskExecutionModeResolver {

    private final StartParameter startParameter;

    public DefaultTaskExecutionModeResolver(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public TaskExecutionMode getExecutionMode(TaskInternal task, TaskProperties properties) {
        // Only false if no declared outputs AND no Task.upToDateWhen spec. We force to true for incremental tasks.
        AndSpec<? super TaskInternal> upToDateSpec = task.getOutputs().getUpToDateSpec();
        if (!properties.hasDeclaredOutputs() && upToDateSpec.isEmpty()) {
            if (task.hasTaskActions()) {
                if (requiresInputChanges(task)) {
                    throw new InvalidUserCodeException("You must declare outputs or use `TaskOutputs.upToDateWhen()` when using the incremental task API");
                } else {
                    return TaskExecutionMode.NO_OUTPUTS_WITH_ACTIONS;
                }
            } else {
                return TaskExecutionMode.NO_OUTPUTS_WITHOUT_ACTIONS;
            }
        }

        if (startParameter.isRerunTasks()) {
            return TaskExecutionMode.RERUN_TASKS_ENABLED;
        }

        if (!upToDateSpec.isSatisfiedBy(task)) {
            return TaskExecutionMode.UP_TO_DATE_WHEN_FALSE;
        }

        return TaskExecutionMode.INCREMENTAL;
    }

    private static boolean requiresInputChanges(TaskInternal task) {
        for (InputChangesAwareTaskAction taskAction : task.getTaskActions()) {
            if (taskAction instanceof AbstractIncrementalTaskAction) {
                return true;
            }
        }
        return false;
    }
}
