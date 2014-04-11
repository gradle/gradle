/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.execution;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.TaskParameter;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link org.gradle.execution.BuildConfigurationAction} which selects tasks which match the task parameter.
 * For each name, selects all tasks in all projects whose name is the given name.
 */
public class TaskParameterResolvingBuildConfigurationAction implements BuildConfigurationAction {
    private final TaskSelector selector;

    public TaskParameterResolvingBuildConfigurationAction(TaskSelector selector) {
        this.selector = selector;
    }

    public void configure(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        StartParameter parameters = gradle.getStartParameter();
        List<TaskParameter> taskParameters = parameters.getTaskParameters();
        if (taskParameters == null || taskParameters.isEmpty()) {
            context.proceed();
            return;
        }
        LinkedHashSet<String> selectedTasks = new LinkedHashSet<String>();
        for (TaskParameter parameter : taskParameters) {
            Set<Task> tasks = selector.getSelection(parameter).getTasks();
            for (String selectedTask : Iterables.transform(
                    tasks,
                    new Function<Task, String>() {
                        public String apply(Task input) {
                            return input.getPath();
                        }
                    })) {
                selectedTasks.add(selectedTask);
            }

        }
        gradle.getStartParameter().setTaskNames(Lists.newArrayList(selectedTasks));

        context.proceed();
    }
}
