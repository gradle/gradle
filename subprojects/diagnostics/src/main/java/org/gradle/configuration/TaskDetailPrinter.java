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

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.NonNullApi;
import org.gradle.internal.InternalTransformer;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;
import static org.gradle.util.internal.CollectionUtils.collect;
import static org.gradle.util.internal.CollectionUtils.sort;

@NonNullApi
public class TaskDetailPrinter {
    private final String taskPath;
    private final List<TaskDetails> tasks;
    private static final String INDENT = "     ";

    public TaskDetailPrinter(String taskPath, List<TaskDetails> tasks) {
        this.taskPath = taskPath;
        this.tasks = tasks;
    }

    public void print(StyledTextOutput output) {
        output.text("Detailed task information for ").withStyle(UserInput).println(taskPath);
        final Map<String, List<TaskDetails>> detailsByTaskType = groupTasksByType(this.tasks);

        final Set<String> typeNames = detailsByTaskType.keySet();
        boolean multipleTaskTypes = typeNames.size() > 1;
        final List<String> sortedTaskTypes = sort(typeNames);
        for (String taskType : sortedTaskTypes) {
            output.println();
            final List<TaskDetails> tasksByType = detailsByTaskType.get(taskType);
            final TaskDetails anyTask = tasksByType.iterator().next();
            String shortTypeName = anyTask.getShortTypeName();
            final LinePrefixingStyledTextOutput pathOutput = createIndentedOutput(output, INDENT);
            pathOutput.println(tasksByType.size() > 1 ? "Paths" : "Path");
            for (TaskDetails task : tasksByType) {
                pathOutput.withStyle(UserInput).println(task.getPath());
            }

            output.println();
            final LinePrefixingStyledTextOutput typeOutput = createIndentedOutput(output, INDENT);
            typeOutput.println("Type");
            typeOutput.withStyle(UserInput).text(shortTypeName);
            typeOutput.println(" (" + taskType + ")");

            printlnCommandlineOptions(output, tasksByType);

            output.println();
            printTaskDescription(output, tasksByType);

            output.println();
            printTaskGroup(output, tasksByType);

            if (multipleTaskTypes) {
                output.println();
                output.println("----------------------");
            }
        }
    }

    private Map<String, List<TaskDetails>> groupTasksByType(Collection<TaskDetails> tasks) {
        return tasks.stream().collect(Collectors.groupingBy(TaskDetails::getTaskType, LinkedHashMap::new, Collectors.toList()));
    }

    private void printTaskDescription(StyledTextOutput output, List<TaskDetails> tasks) {
        printTaskAttribute(output, "Description", tasks, TaskDetails::getDescription);
    }

    private void printTaskGroup(StyledTextOutput output, List<TaskDetails> tasks) {
        printTaskAttribute(output, "Group", tasks, TaskDetails::getGroup);
    }

    private void printTaskAttribute(StyledTextOutput output, String attributeHeader, List<TaskDetails> tasks, InternalTransformer<String, TaskDetails> transformer) {
        int count = collect(tasks, new HashSet<>(), transformer).size();
        final LinePrefixingStyledTextOutput attributeOutput = createIndentedOutput(output, INDENT);
        if (count == 1) {
            // all tasks have the same value
            attributeOutput.println(attributeHeader);
            final TaskDetails task = tasks.iterator().next();
            String value = transformer.transform(task);
            attributeOutput.println(value == null ? "-" : value);
        } else {
            attributeOutput.println(attributeHeader + "s");
            for (TaskDetails task : tasks) {
                attributeOutput.withStyle(UserInput).text("(" + task.getPath() + ") ");
                String value = transformer.transform(task);
                attributeOutput.println(value == null ? "-" : value);
            }
        }
    }

    private void printlnCommandlineOptions(StyledTextOutput output, List<TaskDetails> tasks) {
        List<TaskDetails.OptionDetails> allOptions = new ArrayList<>();
        for (TaskDetails task : tasks) {
            allOptions.addAll(task.getOptions());
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

    private Map<String, Set<String>> optionToAvailableValues(List<TaskDetails.OptionDetails> allOptions) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (TaskDetails.OptionDetails optionDescriptor : allOptions) {
            if (result.containsKey(optionDescriptor.getName())) {
                Collection<String> commonValues = Sets.intersection(optionDescriptor.getAvailableValues(), result.get(optionDescriptor.getName()));
                result.put(optionDescriptor.getName(), new TreeSet<>(commonValues));
            } else {
                result.put(optionDescriptor.getName(), optionDescriptor.getAvailableValues());
            }
        }
        return result;
    }

    private Map<String, String> optionToDescription(List<TaskDetails.OptionDetails> allOptions) {
        Map<String, String> result = new HashMap<>();
        for (TaskDetails.OptionDetails optionDescriptor : allOptions) {
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
