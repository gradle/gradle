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

import org.apache.commons.io.IOUtils;
import org.fusesource.jansi.AnsiOutputStream;
import org.gradle.api.UncheckedIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses rich console output into its pieces for verification in functional tests
 */
public class GroupedOutputFixture {
    /**
     * All tasks will start with > Task, captures everything starting with : and going until a control char
     */
    private final static String TASK_HEADER = "> Task (:[\\w:]*) ?(FAILED|FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE)?\\n?";

    private final static String EMBEDDED_BUILD_START = "> :\\w* > root project";
    private final static String BUILD_STATUS_FOOTER = "BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "FAILURE:";

    /**
     * Various patterns to detect the end of the task output
     */
    private final static String END_OF_TASK_OUTPUT = TASK_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER + "|" + EMBEDDED_BUILD_START;

    private final static String PROGRESS_BAR_PATTERN = "<[-=(\u001b\\[\\d+[a-zA-Z;])]*> \\d+% (INITIALIZ|CONFIGUR|EXECUT)ING \\[((\\d+h )? \\d+m )?\\d+s\\]";
    private final static String WORK_IN_PROGRESS_PATTERN = "\u001b\\[\\d+[a-zA-Z]> (IDLE|[:a-z][\\w\\s\\d:>/\\\\\\.]+)\u001b\\[\\d*[a-zA-Z]";
    private final static String DOWN_MOVEMENT_WITH_NEW_LINE_PATTERN = "\u001b\\[\\d+B\\n";

    private final static String WORK_IN_PROGRESS_AREA_PATTERN = PROGRESS_BAR_PATTERN + "|" + WORK_IN_PROGRESS_PATTERN + "|" + DOWN_MOVEMENT_WITH_NEW_LINE_PATTERN;

    /**
     * Pattern to extract task output.
     */
    private static final Pattern TASK_OUTPUT_PATTERN;

    static {
        String pattern = "(?ms)";
        pattern += TASK_HEADER;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        pattern += "([\\s\\S]*?(?=[^\\n]*?" + END_OF_TASK_OUTPUT + "))";
        TASK_OUTPUT_PATTERN = Pattern.compile(pattern);
    }


    private final String originalOutput;
    private final String strippedOutput;
    private Map<String, GroupedTaskFixture> tasks;

    public GroupedOutputFixture(String output) {
        this.originalOutput = output;
        this.strippedOutput = parse(output);
    }

    private String parse(String output) {
        tasks = new HashMap<String, GroupedTaskFixture>();

        String strippedOutput = stripAnsiCodes(stripWorkInProgressArea(output));
        Matcher matcher = TASK_OUTPUT_PATTERN.matcher(strippedOutput);
        while (matcher.find()) {
            String taskName = matcher.group(1);
            String taskOutcome = matcher.group(2);
            String taskOutput = matcher.group(3).trim();

            GroupedTaskFixture task = tasks.get(taskName);
            if (task == null) {
                task = new GroupedTaskFixture(taskName);
                tasks.put(taskName, task);
            }

            task.addOutput(taskOutput);
            task.setOutcome(taskOutcome);
        }

        return strippedOutput;
    }

    private String stripWorkInProgressArea(String output) {
        String result = output;
        for (int i = 1; i <= 10; ++i) {
            result = result.replaceAll(workInProgressAreaScrollingPattern(i), "");
        }
        return result.replaceAll(WORK_IN_PROGRESS_AREA_PATTERN, "");
    }

    private String workInProgressAreaScrollingPattern(int scroll) {
        return "(\u001b\\[0K\\n){" + scroll + "}\u001b\\[" + scroll + "A";
    }

    private String stripAnsiCodes(String output) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            IOUtils.copy(new StringReader(output), new AnsiOutputStream(baos));
            return baos.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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

    public String getStrippedOutput() {
        return strippedOutput;
    }

    public String toString() {
        return originalOutput;
    }
}
