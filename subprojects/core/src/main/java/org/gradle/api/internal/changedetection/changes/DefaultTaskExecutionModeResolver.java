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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.changedetection.TaskExecutionModeResolver;
import org.gradle.api.internal.project.taskfactory.IncrementalTaskAction;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.specs.AndSpec;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultTaskExecutionModeResolver implements TaskExecutionModeResolver {

    private final StartParameter startParameter;

    public DefaultTaskExecutionModeResolver(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public TaskExecutionMode getExecutionMode(TaskInternal task, TaskProperties properties) {
        if (task.getReasonNotToTrackState().isPresent()) {
            return DefaultTaskExecutionMode.untracked(task.getReasonNotToTrackState().get());
        }
        // Only false if no declared outputs AND no Task.upToDateWhen spec. We force to true for incremental tasks.
        AndSpec<? super TaskInternal> upToDateSpec = task.getOutputs().getUpToDateSpec();
        if (!properties.hasDeclaredOutputs() && upToDateSpec.isEmpty()) {
            if (requiresInputChanges(task)) {
                throw new InvalidUserCodeException("You must declare outputs or use `TaskOutputs.upToDateWhen()` when using the incremental task API");
            } else {
                return DefaultTaskExecutionMode.noOutputs();
            }
        }

        if (startParameter.isRerunTasks()) {
            return DefaultTaskExecutionMode.rerunTasksEnabled();
        }

        if (!upToDateSpec.isSatisfiedBy(task)) {
            return DefaultTaskExecutionMode.upToDateWhenFalse();
        }

        return DefaultTaskExecutionMode.incremental();
    }

    private static boolean requiresInputChanges(TaskInternal task) {
        for (InputChangesAwareTaskAction taskAction : task.getTaskActions()) {
            if (taskAction instanceof IncrementalTaskAction) {
                return true;
            }
        }
        return false;
    }
}
