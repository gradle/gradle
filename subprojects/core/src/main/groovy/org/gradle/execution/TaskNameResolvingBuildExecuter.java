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
import org.gradle.api.internal.tasks.CommandLineOption;
import org.gradle.initialization.CommandLineParser;
import org.gradle.initialization.ParsedCommandLine;
import org.gradle.util.GUtil;
import org.gradle.util.JavaMethod;

import java.lang.reflect.Method;
import java.util.*;

/**
 * A {@link BuildExecuter} which selects tasks which match the provided names. For each name, selects all tasks in all
 * projects whose name is the given name.
 */
public class TaskNameResolvingBuildExecuter implements BuildExecuter {
    private final List<String> names;
    private String description;
    private TaskGraphExecuter executer;
    private final TaskNameResolver taskNameResolver;

    public TaskNameResolvingBuildExecuter(Collection<String> names) {
        this(names, new TaskNameResolver());
    }

    TaskNameResolvingBuildExecuter(Collection<String> names, TaskNameResolver taskNameResolver) {
        this.taskNameResolver = taskNameResolver;
        this.names = new ArrayList<String>(names);
    }

    public List<String> getNames() {
        return names;
    }

    public void select(GradleInternal gradle) {
        Multimap<String, Task> selectedTasks = doSelect(gradle, names, taskNameResolver);

        this.executer = gradle.getTaskGraph();
        for (String name : selectedTasks.keySet()) {
            executer.addTasks(selectedTasks.get(name));
        }
        if (selectedTasks.keySet().size() == 1) {
            description = String.format("primary task %s", GUtil.toString(selectedTasks.keySet()));
        } else {
            description = String.format("primary tasks %s", GUtil.toString(selectedTasks.keySet()));
        }
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
            Map<String, JavaMethod<Task, ?>> options = new HashMap<String, JavaMethod<Task, ?>>();
            if (tasks.size() == 1) {
                for (Class<?> type = tasks.iterator().next().getClass(); type != Object.class; type = type.getSuperclass()) {
                    for (Method method : type.getDeclaredMethods()) {
                        CommandLineOption commandLineOption = method.getAnnotation(CommandLineOption.class);
                        if (commandLineOption != null) {
                            commandLineParser.option(commandLineOption.options()).hasDescription(commandLineOption.description());
                            options.put(commandLineOption.options()[0], new JavaMethod<Task, Object>(Task.class, Object.class, method));
                        }
                    }
                }
            }

            ParsedCommandLine commandLine = commandLineParser.parse(remainingPaths.subList(1, remainingPaths.size()));
            for (Map.Entry<String, JavaMethod<Task, ?>> entry : options.entrySet()) {
                if (commandLine.hasOption(entry.getKey())) {
                    for (Task task : tasks) {
                        entry.getValue().invoke(task, true);
                    }
                }
            }
            remainingPaths = commandLine.getExtraArguments();

            matches.putAll(selector.getTaskName(), tasks);
        }

        return matches;
    }

    public String getDisplayName() {
        return description;
    }

    public void execute() {
        executer.execute();
    }
}
