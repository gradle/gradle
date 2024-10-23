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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses console output into its pieces for verification in functional tests
 *
 * <pre>
 * then:
 * executedAndNotSkipped(":compileJava")
 *
 * result.groupedOutput.task(":compileJava")
 *     .assertOutputContains("Compiling with toolchain")
 * </pre>
 */
public class GroupedOutputFixture {
    /**
     * All tasks will start with {@code > Task}, captures everything starting with : and going until end of line
     */
    private final static String TASK_HEADER = "> Task (:[\\w:]*) ?(FAILED|FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE)?\\n";
    private final static String TRANSFORM_HEADER = "> Transform (file )?([^\\n]+) with ([^\\n]+)\\n";

    private final static String EMBEDDED_BUILD_START = "> :\\w* > [:\\w]+";
    private final static String BUILD_STATUS_FOOTER = "BUILD SUCCESSFUL";
    private final static String BUILD_FAILED_FOOTER = "BUILD FAILED";
    private final static String ACTIONABLE_TASKS = "[0-9]+ actionable tasks?:";

    /**
     * Various patterns to detect the end of the task output
     */
    private final static String END_OF_GROUPED_OUTPUT = TASK_HEADER + "|" + TRANSFORM_HEADER + "|" + BUILD_STATUS_FOOTER + "|" + BUILD_FAILED_FOOTER + "|" + EMBEDDED_BUILD_START + "|" + ACTIONABLE_TASKS + "|\\z";

    /**
     * Pattern to extract task output.
     */
    private static final Pattern TASK_OUTPUT_PATTERN = patternForHeader(TASK_HEADER);

    /**
     * Pattern to extract task output.
     */
    private static final Pattern TRANSFORM_OUTPUT_PATTERN = patternForHeader(TRANSFORM_HEADER);

    private static Pattern patternForHeader(String header) {
        String pattern = "(?ms)";
        pattern += header;
        // Capture all output, lazily up until two new lines and an END_OF_TASK designation
        pattern += "([\\s\\S]*?(?=[^\\n]*?" + END_OF_GROUPED_OUTPUT + "))";
        return Pattern.compile(pattern);
    }

    private final LogContent originalOutput;
    private final String strippedOutput;
    private Map<String, GroupedTaskOutputFixture> tasks;
    private Map<String, GroupedTransformOutputFixture> transforms;

    public GroupedOutputFixture(LogContent output) {
        this.originalOutput = output;
        this.strippedOutput = parse(output);
    }

    private String parse(LogContent output) {
        tasks = new HashMap<>();
        transforms = new HashMap<>();

        String strippedOutput = output.ansiCharsToPlainText().withNormalizedEol();
        findOutputs(strippedOutput, TASK_OUTPUT_PATTERN, this::consumeTaskOutput);
        findOutputs(strippedOutput, TRANSFORM_OUTPUT_PATTERN, this::consumeTransformOutput);

        return strippedOutput;
    }

    private static void findOutputs(String strippedOutput, Pattern outputPattern, Consumer<Matcher> consumer) {
        Matcher matcher = outputPattern.matcher(strippedOutput);
        while (matcher.find()) {
            consumer.accept(matcher);
        }
    }

    public int getTaskCount() {
        return tasks.size();
    }

    public int getTransformCount() {
        return transforms.size();
    }

    public boolean hasTask(String taskName) {
        return tasks.containsKey(taskName);
    }

    public GroupedTaskOutputFixture task(String taskName) {
        boolean foundTask = hasTask(taskName);

        if (!foundTask) {
            throw new AssertionError(String.format("The grouped output for task '%s' could not be found.%nOutput:%n%s", taskName, originalOutput));
        }

        return tasks.get(taskName);
    }

    /**
     * Returns grouped output for the given transformer type.
     */
    public GroupedTransformOutputFixture transform(String transformer) {
        List<GroupedTransformOutputFixture> foundTransforms = transforms.values().stream()
            .filter(transform -> transform.getTransformer().equals(transformer))
            .collect(Collectors.toList());

        if (foundTransforms.size() == 0) {
            throw new AssertionError(String.format("The grouped output for transform '%s' could not be found.%nOutput:%n%s", transformer, originalOutput));
        } else if (foundTransforms.size() > 1) {
            throw new AssertionError(String.format("Multiple grouped outputs for transform '%s' were found. Consider specifying a subject.%nOutput:%n%s", transformer, originalOutput));
        }

        return foundTransforms.get(0);
    }

    /**
     * Returns grouped output for the given transformer type and transform subject.
     */
    public GroupedTransformOutputFixture transform(String transformer, String subject) {
        List<GroupedTransformOutputFixture> foundTransforms = transforms.values().stream()
            .filter(transform -> transform.getTransformer().equals(transformer) && transform.getSubject().equals(subject))
            .collect(Collectors.toList());

        if (foundTransforms.size() == 0) {
            throw new AssertionError(String.format("The grouped output for transform '%s' and subject '%s' could not be found.%nOutput:%n%s", transformer, subject, originalOutput));
        } else if (foundTransforms.size() > 1) {
            throw new AssertionError(String.format("Multiple grouped outputs for transform '%s' and subject '%s' were found.%nOutput:%n%s", transformer, subject, originalOutput));
        }

        return foundTransforms.get(0);
    }

    /**
     * Returns transform subjects for the given transformer type.
     */
    public Set<String> subjectsFor(String transformer) {
        return transforms.values().stream()
                .filter(transform -> transform.getTransformer().equals(transformer))
                .map(GroupedTransformOutputFixture::getSubject)
                .collect(Collectors.toSet());
    }

    public String getStrippedOutput() {
        return strippedOutput;
    }

    @Override
    public String toString() {
        return "Output for tasks: " + Arrays.deepToString(tasks.keySet().stream().sorted().toArray());
    }

    private void consumeTaskOutput(Matcher matcher) {
        String taskName = matcher.group(1);
        String taskOutcome = matcher.group(2);
        String taskOutput = StringUtils.strip(matcher.group(3), "\n");

        GroupedTaskOutputFixture task = tasks.get(taskName);
        if (task == null) {
            task = new GroupedTaskOutputFixture(taskName);
            tasks.put(taskName, task);
        }

        task.addOutput(taskOutput);
        task.setOutcome(taskOutcome);
    }

    private void consumeTransformOutput(Matcher matcher) {
        String initialSubjectType = matcher.group(1);
        String subject = matcher.group(2);
        String transformer = matcher.group(3);
        String transformOutput = StringUtils.strip(matcher.group(4), "\n");

        String key = initialSubjectType + ";" + subject + ";" + transformer;

        GroupedTransformOutputFixture transform = transforms.get(key);
        if (transform == null) {
            transform = new GroupedTransformOutputFixture(initialSubjectType, subject, transformer);
            transforms.put(key, transform);
        }

        transform.addOutput(transformOutput);
    }
}
