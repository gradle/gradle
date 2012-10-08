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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.Task;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.execution.CommandLineTaskConfigurer;
import org.gradle.execution.TaskSelector;

import java.util.List;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 10/8/12
 */
public class CommandLineTaskParser {

    public Multimap<String, Task> parseTasks(List<String> paths, TaskSelector taskSelector) {
        SetMultimap<String, Task> out = LinkedHashMultimap.create();
        List<String> remainingPaths = paths;
        while (!remainingPaths.isEmpty()) {
            String path = remainingPaths.get(0);
            taskSelector.selectTasks(path);
            Set<Task> tasks = taskSelector.getTasks();

            CommandLineParser commandLineParser = new CommandLineParser();
            CommandLineTaskConfigurer.Options options = new CommandLineTaskConfigurer().getConfigurationEntries(commandLineParser, tasks);

            ParsedCommandLine commandLine = commandLineParser.parse(remainingPaths.subList(1, remainingPaths.size()));

            options.maybeConfigure(commandLine, tasks);

            remainingPaths = commandLine.getExtraArguments();

            out.putAll(taskSelector.getTaskName(), tasks);
        }
        return out;
    }
}
