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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.options.OptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.specs.Spec;
import org.gradle.execution.TaskSelector;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.LinePrefixingStyledTextOutput;

import java.util.*;

import static org.gradle.logging.StyledTextOutput.Style.UserInput;
import static org.gradle.util.CollectionUtils.*;

public class TaskDetailPrinter {
    private final String taskPath;
    private final TaskSelector.TaskSelection selection;
    private static final String INDENT = "     ";
    private final OptionReader optionReader;

    public TaskDetailPrinter(String taskPath, TaskSelector.TaskSelection selection, OptionReader optionReader) {
        this.taskPath = taskPath;
        this.selection = selection;
        this.optionReader = optionReader;
    }

    public void print(StyledTextOutput output) {
        final List<Task> tasks = sort(selection.getTasks());

        output.text("Detailed task information for ").withStyle(UserInput).println(taskPath);
        final ListMultimap<Class, Task> classListMap = groupTasksByType(tasks);

        final Set<Class> classes = classListMap.keySet();
        boolean multipleClasses = classes.size() > 1;
        final List<Class> sortedClasses = sort(classes, new Comparator<Class>() {
            public int compare(Class o1, Class o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        for (Class clazz : sortedClasses) {
            output.println();
            final List<Task> tasksByType = classListMap.get(clazz);
            final LinePrefixingStyledTextOutput pathOutput = createIndentedOutput(output, INDENT);
            pathOutput.println(tasksByType.size() > 1 ? "Paths" : "Path");
            for (Task task : tasksByType) {
                pathOutput.withStyle(UserInput).println(task.getPath());
            }
            output.println();
            final LinePrefixingStyledTextOutput typeOutput = createIndentedOutput(output, INDENT);
            typeOutput.println("Type");
            typeOutput.withStyle(UserInput).text(clazz.getSimpleName());
            typeOutput.println(String.format(" (%s)", clazz.getName()));

            printlnCommandlineOptions(output, tasksByType);

            output.println();
            printTaskDescription(output, tasksByType);
            if (multipleClasses) {
                output.println();
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
        taskTypes.addAll(collect(tasks, new Transformer<Class, Task>() {
            public Class transform(Task original) {
                return getDeclaredTaskType(original);
            }
        }));

        ListMultimap<Class, Task> tasksGroupedByType = ArrayListMultimap.create();
        for (final Class taskType : taskTypes) {
            tasksGroupedByType.putAll(taskType, filter(tasks, new Spec<Task>() {
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
        final LinePrefixingStyledTextOutput descriptorOutput = createIndentedOutput(output, INDENT);
        descriptorOutput.println(differentDescriptionsCount > 1 ? "Descriptions" : "Description");
        if (differentDescriptionsCount == 1) {
            // all tasks have the same description
            final Task task = tasks.iterator().next();
            descriptorOutput.println(task.getDescription() == null ? "-" : task.getDescription());
        } else {
            for (Task task : tasks) {
                descriptorOutput.withStyle(UserInput).text(String.format("(%s) ", task.getPath()));
                descriptorOutput.println(task.getDescription() == null ? "-" : task.getDescription());
            }
        }
    }

    private void printlnCommandlineOptions(StyledTextOutput output, List<Task> tasks) {
        List<OptionDescriptor> allOptions = new ArrayList<OptionDescriptor>();
        for (Task task : tasks) {
            allOptions.addAll(optionReader.getOptions(task));
        }
        if (!allOptions.isEmpty()) {
            output.println();
            output.text("Options").println();
        }
        final ListMultimap<String, OptionDescriptor> optionsByName = groupDescriptorsByName(allOptions);
        Iterator<String> optionNames = sort(optionsByName.asMap().keySet()).iterator();
        while (optionNames.hasNext()) {
            final String currentOption = optionNames.next();
            final List<OptionDescriptor> descriptorsForCurrentName = optionsByName.get(currentOption);

            final String optionString = String.format("--%s", currentOption);
            output.text(INDENT).withStyle(UserInput).text(optionString);

            List<List<String>> availableValuesByDescriptor = collect(descriptorsForCurrentName, new Transformer<List<String>, OptionDescriptor>() {
                public List<String> transform(OptionDescriptor original) {
                    return original.getAvailableValues();
                }
            });

            List<String> commonAvailableValues = intersection(availableValuesByDescriptor);
            Set<String> availableValues = new TreeSet<String>(commonAvailableValues);
            //description does not differ between task objects, grab first one
            output.text(INDENT).text(descriptorsForCurrentName.iterator().next().getDescription());
            if (!availableValues.isEmpty()) {
                final int optionDescriptionOffset = 2 * INDENT.length() + optionString.length();
                final LinePrefixingStyledTextOutput prefixedOutput = createIndentedOutput(output, optionDescriptionOffset);
                prefixedOutput.println();
                prefixedOutput.println("Available values are:");
                for (String value : availableValues) {
                    prefixedOutput.text(INDENT);
                    prefixedOutput.withStyle(UserInput).println(value);
                }
            } else {
                output.println();
            }
            if (optionNames.hasNext()) {
                output.println();
            }
        }
    }

    private ListMultimap<String, OptionDescriptor> groupDescriptorsByName(List<OptionDescriptor> allOptions) {
        ListMultimap<String, OptionDescriptor> optionsGroupedByName = ArrayListMultimap.create();
        for (final OptionDescriptor option : allOptions) {
            optionsGroupedByName.put(option.getName(), option);
        }
        return optionsGroupedByName;
    }

    private LinePrefixingStyledTextOutput createIndentedOutput(StyledTextOutput output, int offset) {
        return createIndentedOutput(output, StringUtils.leftPad("", offset, ' '));
    }

    private LinePrefixingStyledTextOutput createIndentedOutput(StyledTextOutput output, String prefix) {
        return new LinePrefixingStyledTextOutput(output, prefix);
    }

    private int differentDescriptions(List<Task> tasks) {
        return toSet(
                collect(tasks, new Transformer<String, Task>() {
                    public String transform(Task original) {
                        return original.getDescription();
                    }
                })
        ).size();
    }
}
