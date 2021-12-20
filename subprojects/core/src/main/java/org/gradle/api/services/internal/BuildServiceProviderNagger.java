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

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.execution.TaskExecutionTracker;

import static org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour;

public class BuildServiceProviderNagger implements BuildServiceProvider.Listener {

    private final TaskExecutionTracker taskExecutionTracker;

    public BuildServiceProviderNagger(TaskExecutionTracker taskExecutionTracker) {
        this.taskExecutionTracker = taskExecutionTracker;
    }

    @Override
    public void beforeGet(BuildServiceProvider<?, ?> provider) {
        taskExecutionTracker.getCurrentTask().ifPresent(task -> {
            if (!task.getRequiredServices().contains(provider)) {
                nagAboutUndeclaredBuildServiceUsage(provider, task);
            }
        });
    }

    private static void nagAboutUndeclaredBuildServiceUsage(BuildServiceProvider<?, ?> serviceProvider, TaskInternal task) {
        deprecateBehaviour(undeclaredBuildServiceUsage(serviceProvider, task))
            .withAdvice("Declare the task uses the build service via 'Task#usesService'.")
            .withContext("Max parallel usages constraint on the build service can't be honored.")
            .willBecomeAnErrorInGradle8()
            .withDslReference(Task.class, "usesService(org.gradle.api.provider.Provider)")
            .nagUser();
    }

    private static String undeclaredBuildServiceUsage(BuildServiceProvider<?, ?> serviceProvider, TaskInternal task) {
        return "Build service '" + serviceProvider.getName() + "'" +
            " is being used by task '" + task.getIdentityPath() + "'" +
            " without the corresponding declaration via 'Task#usesService'.";
    }
}
