/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.commandline;

import com.google.common.collect.Lists;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Task;
import org.gradle.execution.TaskSelector;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CommandLineTaskParser {
    private final CommandLineTaskConfigurer taskConfigurer;
    private final TaskSelector taskSelector;

    public CommandLineTaskParser(CommandLineTaskConfigurer commandLineTaskConfigurer, TaskSelector taskSelector) {
        this.taskConfigurer = commandLineTaskConfigurer;
        this.taskSelector = taskSelector;
    }

    public List<TaskSelector.TaskSelection> parseTasks(TaskExecutionRequest taskExecutionRequest) {
        List<TaskSelector.TaskSelection> out = Lists.newArrayList();
        List<String> remainingPaths = new LinkedList<String>(taskExecutionRequest.getArgs());
        while (!remainingPaths.isEmpty()) {
            String path = remainingPaths.remove(0);
            TaskSelector.TaskSelection selection = taskSelector.getSelection(taskExecutionRequest.getProjectPath(), taskExecutionRequest.getRootDir(), path);
            Set<Task> tasks = selection.getTasks();
            remainingPaths = taskConfigurer.configureTasks(tasks, remainingPaths);
            out.add(selection);
        }
        return out;
    }
}
