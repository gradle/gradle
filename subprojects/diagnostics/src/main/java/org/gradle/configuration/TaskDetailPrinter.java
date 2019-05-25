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
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.options.OptionDescriptor;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.specs.Spec;
import org.gradle.execution.TaskSelector;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;
import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.filter;
import static org.gradle.util.CollectionUtils.sort;

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
            @Override
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
            typeOutput.println(" (" + clazz.getName() + ")");

            printlnCommandlineOptions(output, tasksByType);

            output.println();
            printTaskDescription(output, tasksByType);

            output.println();
            printTaskGroup(output, tasksByType);

            if (multipleClasses) {
                output.println();
                output.println("----------------------");
            }
        }
    }

    private ListMultimap<Class, Task> groupTasksByType(List<Task> tasks) {
        final Set<Class> taskTypes = new TreeSet<Class>(new Comparator<Class>() {
            @Override
            public int compare(Class o1, Class o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        taskTypes.addAll(collect(tasks, new Transformer<Class, Task>() {
            @Override
            public Class transform(Task original) {
                return getDeclaredTaskType(original);
            }
        }));

        ListMultimap<Class, Task> tasksGroupedByType = ArrayListMultimap.create();
        for (final Class taskType : taskTypes) {
            tasksGroupedByType.putAll(taskType, filter(tasks, new Spec<Task>() {
                @Override
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
        printTaskAttribute(output, "Description", tasks, new Transformer<String, Task>() {
            @Override
            public String transform(Task task) {
                return task.getDescription();
            }
        });
    }

    private void printTaskGroup(StyledTextOutput output, List<Task> tasks) {
        printTaskAttribute(output, "Group", tasks, new Transformer<String, Task>() {
            @Override
            public String transform(Task task) {
                return task.getGroup();
            }
        });
    }

    private void printTaskAttribute(StyledTextOutput output, String attributeHeader, List<Task> tasks, Transformer<String, Task> transformer) {
        int count = collect(tasks, new HashSet<String>(), transformer).size();
        final LinePrefixingStyledTextOutput attributeOutput = createIndentedOutput(output, INDENT);
        if (count == 1) {
            // all tasks have the same value
            attributeOutput.println(attributeHeader);
            final Task task = tasks.iterator().next();
            String value = transformer.transform(task);
            attributeOutput.println(value == null ? "-" : value);
        } else {
            attributeOutput.println(attributeHeader + "s");
            for (Task task : tasks) {
                attributeOutput.withStyle(UserInput).text("(" + task.getPath() + ") ");
                String value = transformer.transform(task);
                attributeOutput.println(value == null ? "-" : value);
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
        Map<String, Set<String>> optionToAvailableOptionsValues = optionToAvailableValues(allOptions);
        Map<String, String> optionToDescription = optionToDescription(allOptions);
        Iterator<String> optionNames = optionToAvailableOptionsValues.keySet().iterator();
        while (optionNames.hasNext()) {
            String currentOption = optionNames.next();
            Set<String> availableValues = optionToAvailableOptionsValues.get(currentOption);
            String optionString = "--" + currentOption;
            output.text(INDENT).withStyle(UserInput).text(optionString);
            output.text(INDENT).text(optionToDescription.get(currentOption));
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

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> optionToAvailableValues(List<OptionDescriptor> allOptions) {
        Map<String, Set<String>> result = new LinkedHashMap<String, Set<String>>();
        for (OptionDescriptor optionDescriptor : allOptions) {
            if (result.containsKey(optionDescriptor.getName())) {
                Collection<String> commonValues = Sets.intersection(optionDescriptor.getAvailableValues(), result.get(optionDescriptor.getName()));
                result.put(optionDescriptor.getName(), new TreeSet<String>(commonValues));
            } else {
                result.put(optionDescriptor.getName(), optionDescriptor.getAvailableValues());
            }
        }
        return result;
    }

    private Map<String, String> optionToDescription(List<OptionDescriptor> allOptions) {
        Map<String, String> result = new HashMap<String, String>();
        for (OptionDescriptor optionDescriptor : allOptions) {
            result.put(optionDescriptor.getName(), optionDescriptor.getDescription());
        }
        return result;
    }

    private LinePrefixingStyledTextOutput createIndentedOutput(StyledTextOutput output, int offset) {
        return createIndentedOutput(output, StringUtils.leftPad("", offset, ' '));
    }

    private LinePrefixingStyledTextOutput createIndentedOutput(StyledTextOutput output, String prefix) {
        return new LinePrefixingStyledTextOutput(output, prefix, false);
    }
}
