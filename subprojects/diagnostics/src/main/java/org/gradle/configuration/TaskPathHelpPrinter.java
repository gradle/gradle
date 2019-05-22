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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.internal.logging.text.LinePrefixingStyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;
import static org.gradle.util.CollectionUtils.collect;


class TaskPathHelpPrinter {

    private static final String INDENT = "     ";

    private final TaskPathHelp taskPathHelp;

    TaskPathHelpPrinter(TaskPathHelp taskPathHelp) {
        this.taskPathHelp = taskPathHelp;
    }

    void print(StyledTextOutput output) {
        output.text("Detailed task information for ").withStyle(UserInput).println(taskPathHelp.taskPath);

        for (TaskPathHelp.TaskType taskType : taskPathHelp.taskTypes) {

            output.println();
            LinePrefixingStyledTextOutput pathOutput = createIndentedOutput(output, INDENT);
            Set<String> paths = taskType.attributesByPath.keySet();
            pathOutput.println(paths.size() > 1 ? "Paths" : "Path");
            for (String path : paths) {
                pathOutput.withStyle(UserInput).println(path);
            }

            output.println();
            LinePrefixingStyledTextOutput typeOutput = createIndentedOutput(output, INDENT);
            typeOutput.println("Type");
            typeOutput.withStyle(UserInput).text(taskType.simpleName);
            typeOutput.println(" (" + taskType.qualifiedName + ")");

            printlnCommandlineOptions(output, taskType);

            output.println();
            printTaskDescription(output, taskType);

            output.println();
            printTaskGroup(output, taskType);

            if (taskPathHelp.taskTypes.size() > 1) {
                output.println();
                output.println("----------------------");
            }
        }
    }

    private void printTaskDescription(StyledTextOutput output, TaskPathHelp.TaskType taskType) {
        printTaskAttribute(output, "Description", taskType, attributes -> attributes.description);
    }

    private void printTaskGroup(StyledTextOutput output, TaskPathHelp.TaskType taskType) {
        printTaskAttribute(output, "Group", taskType, attributes -> attributes.group);
    }

    private void printTaskAttribute(StyledTextOutput output, String attributeHeader, TaskPathHelp.TaskType taskType, Transformer<String, TaskPathHelp.TaskAttributes> transformer) {
        int count = collect(taskType.attributesByPath.values(), new HashSet<>(), transformer).size();
        LinePrefixingStyledTextOutput attributeOutput = createIndentedOutput(output, INDENT);
        if (count == 1) {
            // all tasks have the same value
            attributeOutput.println(attributeHeader);
            TaskPathHelp.TaskAttributes task = taskType.attributesByPath.values().iterator().next();
            String value = transformer.transform(task);
            attributeOutput.println(value == null ? "-" : value);
        } else {
            attributeOutput.println(attributeHeader + "s");
            for (Map.Entry<String, TaskPathHelp.TaskAttributes> entry : taskType.attributesByPath.entrySet()) {
                attributeOutput.withStyle(UserInput).text("(" + entry.getKey() + ") ");
                String value = transformer.transform(entry.getValue());
                attributeOutput.println(value == null ? "-" : value);
            }
        }
    }

    private void printlnCommandlineOptions(StyledTextOutput output, TaskPathHelp.TaskType taskType) {

        if (!taskType.options.isEmpty()) {
            output.println();
            output.text("Options").println();
        }
        Iterator<TaskPathHelp.TaskOption> iterator = taskType.options.iterator();
        while (iterator.hasNext()) {
            TaskPathHelp.TaskOption option = iterator.next();
            String optionString = "--" + option.name;
            output.text(INDENT).withStyle(UserInput).text(optionString);
            output.text(INDENT).text(option.description);
            if (!option.availableValues.isEmpty()) {
                int optionDescriptionOffset = 2 * INDENT.length() + optionString.length();
                LinePrefixingStyledTextOutput indentedOutput = createIndentedOutput(output, optionDescriptionOffset);
                indentedOutput.println();
                indentedOutput.println("Available values are:");
                for (String value : option.availableValues) {
                    indentedOutput.text(INDENT);
                    indentedOutput.withStyle(UserInput).println(value);
                }

            } else {
                output.println();
            }
            if (iterator.hasNext()) {
                output.println();
            }
        }
    }

    private LinePrefixingStyledTextOutput createIndentedOutput(StyledTextOutput output, int offset) {
        return createIndentedOutput(output, StringUtils.leftPad("", offset, ' '));
    }

    private LinePrefixingStyledTextOutput createIndentedOutput(StyledTextOutput output, String prefix) {
        return new LinePrefixingStyledTextOutput(output, prefix, false);
    }
}
