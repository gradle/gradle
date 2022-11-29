/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.services.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.execution.TaskExecutionTracker;

import java.util.Optional;

public class BuildServiceProviderValidator implements BuildServiceProvider.Listener {

    private final TaskExecutionTracker taskExecutionTracker;

    public BuildServiceProviderValidator(TaskExecutionTracker taskExecutionTracker) {
        this.taskExecutionTracker = taskExecutionTracker;
    }

    @Override
    public void beforeGet(BuildServiceProvider<?, ?> provider) {
        currentTask().ifPresent(task -> {
            if (!isServiceRequiredBy(task, provider)) {
                throwUndeclaredUsageOf(provider, task);
            }
        });
    }

    private Optional<TaskInternal> currentTask() {
        return taskExecutionTracker.getCurrentTask();
    }

    private static boolean isServiceRequiredBy(TaskInternal task, BuildServiceProvider<?, ?> provider) {
        return task.getRequiredServices().isServiceRequired(provider);
    }

    private static void throwUndeclaredUsageOf(BuildServiceProvider<?, ?> provider, TaskInternal task) {
        throw new UnsupportedOperationException(undeclaredBuildServiceUsage(provider, task));
    }

    private static String undeclaredBuildServiceUsage(BuildServiceProvider<?, ?> provider, TaskInternal task) {
        return "Build service '" + provider.getName() + "'" +
            " is being used by task '" + task.getIdentityPath() + "'" +
            " without the corresponding declaration via 'Task#usesService'.";
    }
}
