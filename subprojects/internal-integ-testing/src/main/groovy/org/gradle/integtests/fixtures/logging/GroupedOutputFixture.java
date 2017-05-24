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
    private final static String TASK_HEADER = "\u001b\\[1m> Task (:[\\w:]*)\u001b\\[m\\n?";

    private final static String BUILD_STATUS_FOOTER = "[^\\n]*?BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "[^\\n]*?FAILURE:";

    /**
     * Various patterns to detect the end of the task output
     */
    private final static String END_OF_TASK_OUTPUT = TASK_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER;

    /**
     * Patterns to remove from the output: cursor movement, progress bar, work-in-progress items and scrolling the console
     */
    private final static String CURSOR_BACK_PATTERN = "\u001b\\[\\d+D";
    private final static String CURSOR_UP_PATTERN = "\u001b\\[\\d+A";
    private final static String CURSOR_DOWN_PATTERN = "\u001b\\[\\d+B";
    private final static String CURSOR_MOVEMENT_PATTERN = CURSOR_BACK_PATTERN + "|" + CURSOR_DOWN_PATTERN + "|" + CURSOR_UP_PATTERN;

    private final static String PROGRESS_BAR_PATTERN = "\u001b\\[1m<[-=]*> \\d+% (INITIALIZING|CONFIGURING|EXECUTING) \\[\\d+s\\]\u001b\\[m";
    private final static String WORK_IN_PROGRESS_PATTERN = "\u001b\\[\\d+m> (IDLE|[:a-z][\\w\\s\\d:]+)\u001b\\[\\d*m";

    private final static String SCROLLING_WORK_IN_PROGRESS_PATTERN = "(\u001b\\[0K\\n)+\u001b\\[\\d+A";

    /**
     * Pattern to extract task output.
     */
    private static final Pattern TASK_OUTPUT_PATTERN;
    private static String precompiledPattern;

    static {
        precompiledPattern = "(?ms)";
        precompiledPattern += TASK_HEADER;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        precompiledPattern += "((.|\\n)*?(?=[^\\n]*?" + END_OF_TASK_OUTPUT + "))";
        TASK_OUTPUT_PATTERN = Pattern.compile(precompiledPattern);
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
        String g = output.replaceAll(SCROLLING_WORK_IN_PROGRESS_PATTERN, "").replace(ERASE_TO_END_OF_LINE, "").replaceAll(CURSOR_MOVEMENT_PATTERN, "").replaceAll(PROGRESS_BAR_PATTERN, "").replaceAll(WORK_IN_PROGRESS_PATTERN, "");
        Matcher matcher = TASK_OUTPUT_PATTERN.matcher(g);
        while (matcher.find()) {
            String taskName = matcher.group(1);
            String taskOutput = matcher.group(2);
            taskOutput = taskOutput.trim();
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

    public boolean hasTask(String taskName) {
        return tasks.containsKey(taskName);
    }

    public GroupedTaskFixture task(String taskName) {
        boolean foundTask = hasTask(taskName);

        if (!foundTask) {
            throw new AssertionError(String.format("The grouped output for task '%s' could not be found", taskName));
        }

        return tasks.get(taskName);
    }
}
