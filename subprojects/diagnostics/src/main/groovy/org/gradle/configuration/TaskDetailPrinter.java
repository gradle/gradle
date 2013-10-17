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
package org.gradle.configuration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.CommandLineOptionReader;
import org.gradle.api.specs.Spec;
import org.gradle.execution.TaskSelector;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.CollectionUtils;

import java.util.*;

import static org.gradle.logging.StyledTextOutput.Style.UserInput;

public class TaskDetailPrinter {
    private final String taskPath;
    private final TaskSelector.TaskSelection selection;
    private static final String INDENT = "     ";
    private final CommandLineOptionReader commandLineOptionReader;

    public TaskDetailPrinter(String taskPath, TaskSelector.TaskSelection selection) {
        this.taskPath = taskPath;
        this.selection = selection;
        commandLineOptionReader = new CommandLineOptionReader();
    }

    public void print(StyledTextOutput output) {
        final List<Task> tasks = CollectionUtils.sort(selection.getTasks());

        output.text("Detailed task information for ").withStyle(UserInput).println(taskPath);
        final ListMultimap<Class, Task> classListMap = groupTasksByType(tasks);

        final Set<Class> classes = classListMap.keySet();
        boolean multipleClasses = classes.size() > 1;
        final List<Class> sortedClasses = CollectionUtils.sort(classes, new Comparator<Class>() {
            public int compare(Class o1, Class o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        for (Class clazz : sortedClasses) {
            output.println();
            final List<Task> tasksByType = classListMap.get(clazz);
            output.text(tasksByType.size() > 1 ? "Paths" : "Path").println();
            for (Task task : tasksByType) {
                output.text(INDENT).withStyle(UserInput).println(task.getPath());
            }
            output.println();
            output.text("Type").println();
            output.text(INDENT).withStyle(UserInput).text(clazz.getSimpleName());
            output.println(String.format(" (%s)", clazz.getName()));

            printlnCommandlineOptions(output, clazz);

            output.println();
            printTaskDescription(output, tasksByType);
            output.println();
            if (multipleClasses) {
                output.println("----------------------");
            }
        }
    }

    private ListMultimap<Class, Task> groupTasksByType(List<Task> tasks) {
        final Set<Class> taskTypes = new TreeSet<Class>(new Comparator<Class>() {
            public int compare(Class o1, Class o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        taskTypes.addAll(CollectionUtils.collect(tasks, new Transformer<Class, Task>() {
            public Class transform(Task original) {
                return getDeclaredTaskType(original);
            }
        }));

        ListMultimap<Class, Task> tasksGroupedByType = ArrayListMultimap.create();
        for (final Class taskType : taskTypes) {
            tasksGroupedByType.putAll(taskType, CollectionUtils.filter(tasks, new Spec<Task>() {
                public boolean isSatisfiedBy(Task element) {
                    return getDeclaredTaskType(element).equals(taskType);
                }
            }));
        }
        return tasksGroupedByType;
    }

    private Class getDeclaredTaskType(Task original) {
        Class clazz = new DslObject(original).getDeclaredType();
        if (clazz.equals(DefaultTask.class)) {
            return org.gradle.api.Task.class;
        } else {
            return clazz;
        }
    }

    private void printTaskDescription(StyledTextOutput output, List<Task> tasks) {
        int differentDescriptionsCount = differentDescriptions(tasks);
        output.text(differentDescriptionsCount > 1 ? "Descriptions" : "Description").println();
        if (differentDescriptionsCount == 1) {
            // all tasks have the same description
            final Task task = tasks.iterator().next();
            output.text(INDENT).println(task.getDescription() == null ? "-" : task.getDescription());
        } else {
            for (Task task : tasks) {
                output.text(INDENT).withStyle(UserInput).text(String.format("(%s) ", task.getPath()));
                output.println(task.getDescription() == null ? "-" : task.getDescription());
            }
        }
    }

    private void printlnCommandlineOptions(StyledTextOutput output, Class clazz) {
        final List<CommandLineOptionReader.CommandLineOptionDescriptor> commandLineOptions = commandLineOptionReader.getCommandLineOptions(clazz);
        if (!commandLineOptions.isEmpty()) {
            output.println();
            output.text("Options").println();
        }
        final Iterator<CommandLineOptionReader.CommandLineOptionDescriptor> optionsIterator = commandLineOptions.iterator();
        while (optionsIterator.hasNext()) {
            final CommandLineOptionReader.CommandLineOptionDescriptor descriptor = optionsIterator.next();
            final String optionString = String.format("--%s", descriptor.getOption().options()[0]);
            output.text(INDENT).withStyle(UserInput).text(optionString);
            output.text(INDENT).println(descriptor.getOption().description());

            final Class availableValuesType = descriptor.getAvailableValuesType();
            if (availableValuesType == Boolean.class || availableValuesType == Boolean.TYPE) {
                indentForOptionDescription(output, optionString);
                output.text("Takes a boolean value (").withStyle(UserInput).text("true");
                output.text("|").withStyle(UserInput).text("false");
                output.println(") as parameter. As a default (ommitting a parameter) true will be used.");
            }

            if (availableValuesType.isEnum()) {
                final Object[] enumConstants = availableValuesType.getEnumConstants();
                indentForOptionDescription(output, optionString);
                output.text("Takes an enum value of type (").withStyle(UserInput).text(availableValuesType.getName()).println(").");
                indentForOptionDescription(output, optionString);
                output.println("Available values are:");
                for (Object enumConstant : enumConstants) {
                    indentForOptionDescription(output, optionString);
                    output.text(INDENT);
                    output.withStyle(UserInput).println(enumConstant);
                }
            }
            if (optionsIterator.hasNext()) {
                output.println();
            }
        }
    }

    // TODO possibly styledtextoutput should handle proper indention
    // instead of doing it here
    private void indentForOptionDescription(StyledTextOutput output, String optionString) {
        output.text(INDENT).text(INDENT);
        for (int i = 0; i < optionString.length(); i++) {
            output.append(' ');
        }
    }

    private int differentDescriptions(List<Task> tasks) {
        return CollectionUtils.toSet(
                CollectionUtils.collect(tasks, new Transformer<String, Task>() {
                    public String transform(Task original) {
                        return original.getDescription();
                    }
                })
        ).size();
    }
}
