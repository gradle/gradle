/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.collect.Multimap;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.commandline.CommandLineTaskParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A {@link BuildConfigurationAction} which selects tasks which match the provided names. For each name, selects all tasks in all
 * projects whose name is the given name.
 */
public class TaskNameResolvingBuildConfigurationAction implements BuildConfigurationAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskNameResolvingBuildConfigurationAction.class);
    private final CommandLineTaskParser commandLineTaskParser;

    public TaskNameResolvingBuildConfigurationAction(CommandLineTaskParser commandLineTaskParser) {
        this.commandLineTaskParser = commandLineTaskParser;
    }

    public void configure(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        TaskGraphExecuter executer = gradle.getTaskGraph();

        List<TaskExecutionRequest> taskParameters = gradle.getStartParameter().getTaskRequests();
        for (TaskExecutionRequest taskParameter : taskParameters) {
            Multimap<String, Task> selectedTasks = commandLineTaskParser.parseTasks(taskParameter);
            for (String name : selectedTasks.keySet()) {
                LOGGER.info("Selected primary task '{}' from project {}", name, taskParameter.getProjectPath());
                executer.addTasks(selectedTasks.get(name));
            }
        }

        context.proceed();
    }

}
