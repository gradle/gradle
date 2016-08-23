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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.ConstructingTaskResolver;
import org.gradle.api.tasks.TaskContainer;

public class CompositeConstructingTaskResolver implements ConstructingTaskResolver {

    // TODO:DAZ Should return a reference to a task in another build, rather than relying on a synthetic delegating task.
    @Override
    public Task constructTask(final String path, TaskContainer tasks) {
        if (!path.contains("::")) {
            return null;
        }

        Task task = tasks.findByName(path);

        if (task == null) {
            String[] split = path.split("::", 2);
            final String buildName = split[0];
            final String taskToExecute = ":" + split[1];

            // TODO:DAZ Should probably be validating build name here, rather than waiting until execution

            task = tasks.create(path, CompositeBuildTaskDelegate.class, new Action<CompositeBuildTaskDelegate>() {
                @Override
                public void execute(CompositeBuildTaskDelegate compositeBuildTaskDelegate) {
                    compositeBuildTaskDelegate.setBuild(buildName);
                    compositeBuildTaskDelegate.setTask(taskToExecute);
                }
            });
        }
        return task;
    }
}
