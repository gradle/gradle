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

import org.apache.commons.lang3.StringUtils;
import org.gradle.integtests.fixtures.executer.LogContent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses console output into its pieces for verification in functional tests
 */
public class GroupedOutputFixture {
    /**
     * All tasks will start with > Task, captures everything starting with : and going until end of line
     */
    private final static String TASK_HEADER = "> Task (:[\\w:]*) ?(FAILED|FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE)?\\n";
    private final static String TRANSFORMATION_HEADER = "> Transform (artifact|file) ([^\\n]+) with ([^\\n]+)\\n";

    private final static String EMBEDDED_BUILD_START = "> :\\w* > [:\\w]+";
    private final static String BUILD_STATUS_FOOTER = "BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "BUILD FAILED";
    private final static String ACTIONABLE_TASKS = "[0-9]+ actionable tasks?:";

    /**
     * Various patterns to detect the end of the task output
     */
    private final static String END_OF_GROUPED_OUTPUT = TASK_HEADER + "|" + TRANSFORMATION_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER + "|" + EMBEDDED_BUILD_START + "|" + ACTIONABLE_TASKS + "|\\z";

    /**
     * Pattern to extract task output.
     */
    private static final Pattern TASK_OUTPUT_PATTERN = patternForHeader(TASK_HEADER);

    /**
     * Pattern to extract task output.
     */
    private static final Pattern TRANSFORMATION_OUTPUT_PATTERN = patternForHeader(TRANSFORMATION_HEADER);

    private static Pattern patternForHeader(String header) {
        String pattern = "(?ms)";
        pattern += header;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        pattern += "([\\s\\S]*?(?=[^\\n]*?" + END_OF_GROUPED_OUTPUT + "))";
        return Pattern.compile(pattern);
    }

    private final LogContent originalOutput;
    private final String strippedOutput;
    private Map<String, GroupedTaskFixture> tasks;
    private Map<String, GroupedTransformationFixture> transformations;

    public GroupedOutputFixture(LogContent output) {
        this.originalOutput = output;
        this.strippedOutput = parse(output);
    }

    private String parse(LogContent output) {
        tasks = new HashMap<String, GroupedTaskFixture>();
        transformations = new HashMap<String, GroupedTransformationFixture>();

        String strippedOutput = output.removeAnsiChars().withNormalizedEol();
        findOutputs(strippedOutput, TASK_OUTPUT_PATTERN, this::consumeTaskOutput);
        findOutputs(strippedOutput, TRANSFORMATION_OUTPUT_PATTERN, this::consumeTransformationOutput);

        return strippedOutput;
    }

    private void findOutputs(String strippedOutput, Pattern outputPattern, Consumer<Matcher> consumer) {
        Matcher matcher = outputPattern.matcher(strippedOutput);
        while (matcher.find()) {
            consumer.accept(matcher);
        }
    }

    public int getTaskCount() {
        return tasks.size();
    }

    public int getTransformationCount() {
        return transformations.size();
    }

    public boolean hasTask(String taskName) {
        return tasks.containsKey(taskName);
    }

    public GroupedTaskFixture task(String taskName) {
        boolean foundTask = hasTask(taskName);

        if (!foundTask) {
            throw new AssertionError(String.format("The grouped output for task '%s' could not be found.%nOutput:%n%s", taskName, originalOutput));
        }

        return tasks.get(taskName);
    }

    public Set<String> subjectsFor(String transformer) {
        return transformations.values().stream()
                .filter(transformation -> transformation.getTransformer().equals(transformer))
                .map(transformation -> transformation.getSubject())
                .collect(Collectors.toSet());
    }

    public String getStrippedOutput() {
        return strippedOutput;
    }

    public String toString() {
        return originalOutput.withNormalizedEol();
    }

    private void consumeTaskOutput(Matcher matcher) {
        String taskName = matcher.group(1);
        String taskOutcome = matcher.group(2);
        String taskOutput = StringUtils.strip(matcher.group(3), "\n");

        GroupedTaskFixture task = tasks.get(taskName);
        if (task == null) {
            task = new GroupedTaskFixture(taskName);
            tasks.put(taskName, task);
        }

        task.addOutput(taskOutput);
        task.setOutcome(taskOutcome);
    }

    private void consumeTransformationOutput(Matcher matcher) {
        String initialSubjectType = matcher.group(1);
        String subject = matcher.group(2);
        String transformer = matcher.group(3);
        String transformationOutput = StringUtils.strip(matcher.group(4), "\n");

        String key = initialSubjectType + ";" + subject + ";" + transformer;

        GroupedTransformationFixture transformation = transformations.get(key);
        if (transformation == null) {
            transformation = new GroupedTransformationFixture(initialSubjectType, subject, transformer);
            transformations.put(key, transformation);
        }

        transformation.addOutput(transformationOutput);
    }
}
