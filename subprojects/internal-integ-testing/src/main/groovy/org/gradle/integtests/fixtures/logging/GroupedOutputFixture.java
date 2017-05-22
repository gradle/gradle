/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.logging;

import org.apache.commons.collections.map.HashedMap;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses rich console output into its pieces for verification in functional tests
 */
public class GroupedOutputFixture {

    //TODO: Combine with AbstractConsoleTest
    protected final static String ERASE_TO_END_OF_LINE = "\u001B[0K";

    /**
     * All tasks will start with > Task, captures everything starting with : and going until a control char
     */
    private final static String TASK_HEADER = "> Task (:[\\w:]*)[^\\n]*\\n";

    private final static String BUILD_STATUS_FOOTER = "\\n[^\\n]*?BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "\\n[^\\n]*?FAILURE:";
    private final static String PROGRESS_BAR = ".?\\[0K.*?";
    private final static String END_OF_TASK_OUTPUT = TASK_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER + "|" + PROGRESS_BAR;


    private static final Pattern TASK_OUTPUT_PATTERN;
    private static String precompiledPattern;

    static {
        precompiledPattern = "(?ms)";
        precompiledPattern += TASK_HEADER;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        precompiledPattern += "(.*?(?=\\n\\n(?:[^\\n]*?" + END_OF_TASK_OUTPUT + ")))";
        TASK_OUTPUT_PATTERN = Pattern.compile(precompiledPattern);
        //TODO: Remove once stable
        System.err.println(precompiledPattern);
    }

    /**
     * Don't need this if we parse all of the output during construction
     */
    private final String output;
    private Map<String, GroupedTaskFixture> tasks;

    public GroupedOutputFixture(String output) {
        this.output = output;
        parse(output);
    }

    private void parse(String output) {
        tasks = new HashedMap();
        Matcher matcher = TASK_OUTPUT_PATTERN.matcher(output);
        while (matcher.find()) {
            String taskName = matcher.group(1);
            String taskOutput = matcher.group(2);
            taskOutput = taskOutput.replace(ERASE_TO_END_OF_LINE, "");
            if (tasks.containsKey(taskName)) {
                tasks.get(taskName).addOutput(taskOutput);
            } else {
                GroupedTaskFixture task = new GroupedTaskFixture(taskName);
                task.addOutput(taskOutput);
                tasks.put(taskName, task);
            }
        }
    }

    public int getTaskCount() {
        return tasks.size();
    }

    public GroupedTaskFixture task(String taskName) {
        if (tasks.containsKey(taskName)) {
            return tasks.get(taskName);
        }
        return new GroupedTaskFixture(taskName);
    }
}
