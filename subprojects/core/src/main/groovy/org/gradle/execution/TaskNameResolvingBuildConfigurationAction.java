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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * A {@link BuildConfigurationAction} which selects tasks which match the provided names. For each name, selects all tasks in all
 * projects whose name is the given name.
 */
public class TaskNameResolvingBuildConfigurationAction implements BuildConfigurationAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskNameResolvingBuildConfigurationAction.class);
    private final TaskNameResolver taskNameResolver;
    private CommandLineTaskConfigurer commandLineTaskConfigurer = new CommandLineTaskConfigurer();

    public TaskNameResolvingBuildConfigurationAction() {
        this(new TaskNameResolver());
    }

    TaskNameResolvingBuildConfigurationAction(TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
    }

    public void configure(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        List<String> taskNames = gradle.getStartParameter().getTaskNames();
        Multimap<String, Task> selectedTasks = doSelect(gradle, taskNames, taskNameResolver);

        TaskGraphExecuter executer = gradle.getTaskGraph();
        for (String name : selectedTasks.keySet()) {
            executer.addTasks(selectedTasks.get(name));
        }

        if (selectedTasks.keySet().size() == 1) {
            LOGGER.info("Selected primary task {}", GUtil.toString(selectedTasks.keySet()));
        } else {
            LOGGER.info("Selected primary tasks {}", GUtil.toString(selectedTasks.keySet()));
        }

        context.proceed();
    }

    private Multimap<String, Task> doSelect(GradleInternal gradle, List<String> paths, TaskNameResolver taskNameResolver) {
        SetMultimap<String, Task> matches = LinkedHashMultimap.create();
        TaskSelector selector = new TaskSelector(taskNameResolver);
        List<String> remainingPaths = paths;
        while (!remainingPaths.isEmpty()) {
            String path = remainingPaths.get(0);
            selector.selectTasks(gradle, path);

            CommandLineParser commandLineParser = new CommandLineParser();
            Set<Task> tasks = selector.getTasks();

            CommandLineTaskConfigurer.Options options = commandLineTaskConfigurer.getConfigurationEntries(commandLineParser, tasks);

            ParsedCommandLine commandLine = commandLineParser.parse(remainingPaths.subList(1, remainingPaths.size()));

            options.maybeConfigure(commandLine, tasks);

            remainingPaths = commandLine.getExtraArguments();

            matches.putAll(selector.getTaskName(), tasks);
        }

        return matches;
    }
}
