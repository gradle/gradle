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

import org.gradle.TaskExecutionRequest;
import org.gradle.api.Task;
import org.gradle.execution.TaskSelection;
import org.gradle.execution.selection.BuildTaskSelector;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@ServiceScope(Scope.Gradle.class)
public class CommandLineTaskParser {
    private final CommandLineTaskConfigurer taskConfigurer;
    private final BuildTaskSelector taskSelector;
    private final BuildState targetBuild;

    public CommandLineTaskParser(CommandLineTaskConfigurer commandLineTaskConfigurer, BuildTaskSelector taskSelector, BuildState targetBuild) {
        this.taskConfigurer = commandLineTaskConfigurer;
        this.taskSelector = taskSelector;
        this.targetBuild = targetBuild;
    }

    public List<TaskSelection> parseTasks(TaskExecutionRequest taskExecutionRequest) {
        List<TaskSelection> out = new ArrayList<>();
        List<String> remainingPaths = new LinkedList<String>(taskExecutionRequest.getArgs());
        while (!remainingPaths.isEmpty()) {
            String path = remainingPaths.remove(0);
            TaskSelection selection = taskSelector.resolveTaskName(taskExecutionRequest.getRootDir(), taskExecutionRequest.getProjectPath(), targetBuild, path);
            Set<Task> tasks = selection.getTasks();
            remainingPaths = taskConfigurer.configureTasks(tasks, remainingPaths);
            out.add(selection);
        }
        return out;
    }
}
