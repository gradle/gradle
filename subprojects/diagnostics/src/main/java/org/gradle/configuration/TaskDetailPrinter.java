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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;
import static org.gradle.util.CollectionUtils.collect;


class TaskDetailPrinter {

    private static final String INDENT = "     ";

    private final HelpTaskDetail state;

    TaskDetailPrinter(HelpTaskDetail state) {
        this.state = state;
    }

    void print(StyledTextOutput output) {
        output.text("Detailed task information for ").withStyle(UserInput).println(state.getTaskPath());

        List<HelpTaskDetail.SelectedTaskType> taskTypes = state.getSelectedTaskTypes();
        for (HelpTaskDetail.SelectedTaskType taskType : taskTypes) {

            output.println();
            final LinePrefixingStyledTextOutput pathOutput = createIndentedOutput(output, INDENT);
            Set<String> paths = taskType.getAttributesByPath().keySet();
            pathOutput.println(paths.size() > 1 ? "Paths" : "Path");
            for (String path : paths) {
                pathOutput.withStyle(UserInput).println(path);
            }

            output.println();
            final LinePrefixingStyledTextOutput typeOutput = createIndentedOutput(output, INDENT);
            typeOutput.println("Type");
            typeOutput.withStyle(UserInput).text(taskType.getSimpleName());
            typeOutput.println(" (" + taskType.getQualifiedName() + ")");

            printlnCommandlineOptions(output, taskType);

            output.println();
            printTaskDescription(output, taskType);

            output.println();
            printTaskGroup(output, taskType);

            if (taskTypes.size() > 1) {
                output.println();
                output.println("----------------------");
            }
        }
    }

    private void printTaskDescription(StyledTextOutput output, HelpTaskDetail.SelectedTaskType taskType) {
        printTaskAttribute(output, "Description", taskType, attributes -> attributes.getDescription());
    }

    private void printTaskGroup(StyledTextOutput output, HelpTaskDetail.SelectedTaskType taskType) {
        printTaskAttribute(output, "Group", taskType, attributes -> attributes.getGroup());
    }

    private void printTaskAttribute(StyledTextOutput output, String attributeHeader, HelpTaskDetail.SelectedTaskType taskType, Transformer<String, HelpTaskDetail.TaskAttributes> transformer) {
        int count = collect(taskType.getAttributesByPath().values(), new HashSet<>(), transformer).size();
        final LinePrefixingStyledTextOutput attributeOutput = createIndentedOutput(output, INDENT);
        if (count == 1) {
            // all tasks have the same value
            attributeOutput.println(attributeHeader);
            final HelpTaskDetail.TaskAttributes task = taskType.getAttributesByPath().values().iterator().next();
            String value = transformer.transform(task);
            attributeOutput.println(value == null ? "-" : value);
        } else {
            attributeOutput.println(attributeHeader + "s");
            for (Map.Entry<String, HelpTaskDetail.TaskAttributes> entry : taskType.getAttributesByPath().entrySet()) {
                attributeOutput.withStyle(UserInput).text("(" + entry.getKey() + ") ");
                String value = transformer.transform(entry.getValue());
                attributeOutput.println(value == null ? "-" : value);
            }
        }
    }

    private void printlnCommandlineOptions(StyledTextOutput output, HelpTaskDetail.SelectedTaskType taskType) {

        if (!taskType.getOptions().isEmpty()) {
            output.println();
            output.text("Options").println();
        }
        Iterator<HelpTaskDetail.TaskOption> iterator = taskType.getOptions().iterator();
        while (iterator.hasNext()) {
            HelpTaskDetail.TaskOption option = iterator.next();
            String optionString = "--" + option.getName();
            output.text(INDENT).withStyle(UserInput).text(optionString);
            output.text(INDENT).text(option.getDescription());
            if (!option.getAvailableValues().isEmpty()) {
                final int optionDescriptionOffset = 2 * INDENT.length() + optionString.length();
                final LinePrefixingStyledTextOutput prefixedOutput = createIndentedOutput(output, optionDescriptionOffset);
                prefixedOutput.println();
                prefixedOutput.println("Available values are:");
                for (String value : option.getAvailableValues()) {
                    prefixedOutput.text(INDENT);
                    prefixedOutput.withStyle(UserInput).println(value);
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
