/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures.executer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.integtests.fixtures.logging.GroupedOutputFixture;
import org.gradle.internal.Pair;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.launcher.daemon.client.DaemonStartupMessage;
import org.gradle.launcher.daemon.server.DaemonStateCoordinator;
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;
import org.spockframework.runtime.SpockAssertionError;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class OutputScrapingExecutionResult implements ExecutionResult {
    // This monster is to find lines in our logs that look like stack traces
    // We want to match lines that contain just packages and classes:
    // at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.lambda$executeIfValid$1(ExecuteActionsTaskExecuter.java:145)
    // and with module names:
    // at java.base/java.lang.Thread.dumpStack(Thread.java:1383)
    static final Pattern STACK_TRACE_ELEMENT = Pattern.compile("\\s+(at\\s+)?([\\w.$_]+/)?[a-zA-Z_][\\w.$]+\\.[\\w$_ =+'-<>]+\\(.+?\\)(\\x1B\\[0K)?");
    private static final String TASK_PREFIX = "> Task ";

    //for example: ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a'
    private static final Pattern SKIPPED_TASK_PATTERN = Pattern.compile("(> Task )?(:\\S+?(:\\S+?)*)\\s+((SKIPPED)|(UP-TO-DATE)|(NO-SOURCE)|(FROM-CACHE))");

    //for example: ':hey' or ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a FOO'
    private static final Pattern TASK_PATTERN = Pattern.compile("(> Task )?(:\\S+?(:\\S+?)*)((\\s+SKIPPED)|(\\s+UP-TO-DATE)|(\\s+FROM-CACHE)|(\\s+NO-SOURCE)|(\\s+FAILED)|(\\s*))");

    private static final Pattern BUILD_RESULT_PATTERN = Pattern.compile("(BUILD|CONFIGURE) (SUCCESSFUL|FAILED) in( \\d+m?[smh])+");

    private final LogContent output;
    private final LogContent error;
    private final boolean includeBuildSrc;
    private final LogContent mainContent;
    private final LogContent postBuild;
    private final LogContent errorContent;
    private GroupedOutputFixture groupedOutputFixture;
    private Set<String> tasks;

    public static List<String> flattenTaskPaths(Object[] taskPaths) {
        return CollectionUtils.toStringList(GUtil.flatten(taskPaths, Lists.newArrayList()));
    }

    /**
     * Creates a result from the output of a <em>single</em> Gradle invocation.
     *
     * @param output The raw build stdout chars.
     * @param error The raw build stderr chars.
     * @return A {@link OutputScrapingExecutionResult} for a successful build, or a {@link OutputScrapingExecutionFailure} for a failed build.
     */
    public static OutputScrapingExecutionResult from(String output, String error) {
        // Should provide a Gradle version as parameter so this check can be more precise
        if (output.contains("BUILD FAILED") || output.contains("FAILURE: Build failed with an exception.") || error.contains("BUILD FAILED") || error.contains("CONFIGURE FAILED")) {
            return new OutputScrapingExecutionFailure(output, error, true);
        }
        return new OutputScrapingExecutionResult(LogContent.of(output), LogContent.of(error), true);
    }

    /**
     * @param output The build stdout content.
     * @param error The build stderr content. Must have normalized line endings.
     */
    protected OutputScrapingExecutionResult(LogContent output, LogContent error, boolean includeBuildSrc) {
        this.output = output;
        this.error = error;
        this.includeBuildSrc = includeBuildSrc;

        // Split out up the output into main content and post build content
        LogContent filteredOutput = this.output.ansiCharsToPlainText().removeDebugPrefix();
        Pair<LogContent, LogContent> match = filteredOutput.splitOnFirstMatchingLine(BUILD_RESULT_PATTERN);
        if (match == null) {
            this.mainContent = filteredOutput;
            this.postBuild = LogContent.empty();
        } else {
            this.mainContent = match.getLeft();
            this.postBuild = match.getRight().drop(1);
        }
        this.errorContent = error.ansiCharsToPlainText();
    }

    @Override
    public ExecutionResult getIgnoreBuildSrc() {
        return new OutputScrapingExecutionResult(output, error, false);
    }

    @Override
    public String getOutput() {
        return output.withNormalizedEol();
    }

    /**
     * The main content with debug prefix and ANSI characters removed.
     */
    public LogContent getMainContent() {
        return mainContent;
    }

    /**
     * The content after the build successful message with debug prefix and ANSI characters removed.
     */
    public LogContent getPostBuildContent() {
        return postBuild;
    }

    @Override
    public String getNormalizedOutput() {
        return normalize(output);
    }

    @Override
    public String getFormattedOutput() {
        return output.ansiCharsToColorText().withNormalizedEol();
    }

    @Override
    public String getPlainTextOutput() {
        return output.ansiCharsToPlainText().withNormalizedEol();
    }

    @Override
    public GroupedOutputFixture getGroupedOutput() {
        if (groupedOutputFixture == null) {
            groupedOutputFixture = new GroupedOutputFixture(getMainContent());
        }
        return groupedOutputFixture;
    }

    private String normalize(LogContent output) {
        List<String> result = new ArrayList<>();
        List<String> lines = output.getLines();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.contains(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)) {
                // Remove the "daemon starting" message
                i++;
            } else if (line.contains(DaemonStateCoordinator.DAEMON_WILL_STOP_MESSAGE)) {
                // Remove the "Daemon will be shut down" message
                i++;
            } else if (line.contains(HealthExpirationStrategy.EXPIRE_DAEMON_MESSAGE)) {
                // Remove the "The Daemon will expire" message
                i+=7;
            } else if (line.contains(LoggingDeprecatedFeatureHandler.WARNING_SUMMARY)) {
                // Remove the deprecations message: "Deprecated Gradle features...", "Use '--warning-mode all'...", "See https://docs.gradle.org...", and additional newline
                i+=4;
            } else if (BUILD_RESULT_PATTERN.matcher(line).matches()) {
                result.add(BUILD_RESULT_PATTERN.matcher(line).replaceFirst("$1 $2 in 0s"));
                i++;
            } else {
                result.add(normalizeLambdaIds(line));
                i++;
            }
        }

        return LogContent.of(result).withNormalizedEol();
    }

    /**
     * Normalize the non-deterministic part of lambda class name.
     *
     * Lambdas do have some non-deterministic class names, depending on when they are loaded.
     * Since we want to assert the Lambda class name for some deprecation warning tests, we replace the non-deterministic part by {@code <non-deterministic>}.
     */
    public static String normalizeLambdaIds(@Nullable String line) {
        return line == null ? null : line.replaceAll("\\$\\$Lambda\\$[0-9]+/(0x)?[0-9a-f]+", "\\$\\$Lambda\\$<non-deterministic>");
    }

    @Override
    public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
        SequentialOutputMatcher matcher = ignoreLineOrder ? new AnyOrderOutputMatcher() : new SequentialOutputMatcher();
        matcher.assertOutputMatches(expectedOutput, getNormalizedOutput(), ignoreExtraLines);
        return this;
    }

    @Override
    public ExecutionResult assertHasPostBuildOutput(String expectedOutput) {
        return assertContentContains(postBuild.withNormalizedEol(), expectedOutput, "Post-build output");
    }

    @Override
    public ExecutionResult assertNotPostBuildOutput(String expectedOutput) {
        String expectedText = LogContent.of(expectedOutput).withNormalizedEol();
        if (postBuild.withNormalizedEol().contains(expectedText)) {
            failureOnUnexpectedOutput(String.format("Found unexpected text in post-build output.%nExpected not present: %s%n", expectedText));
        }
        return this;
    }

    @Override
    public ExecutionResult assertNotOutput(String expectedOutput) {
        String expectedText = LogContent.of(expectedOutput).withNormalizedEol();
        if (getOutput().contains(expectedText)|| getError().contains(expectedText)) {
            failureOnUnexpectedOutput(String.format("Found unexpected text in build output.%nExpected not present: %s%n", expectedText));
        }
        return this;
    }

    private static class LineWithDistance {
        private final String line;
        private final int distance;

        public LineWithDistance(String line, int distance) {
            this.line = line;
            this.distance = distance;
        }

        public String getLine() {
            return line;
        }

        public int getDistance() {
            return distance;
        }
    }

    @Override
    public ExecutionResult assertContentContains(String actualText, String expectedOutput, String label) {
        String expectedText = LogContent.of(expectedOutput).withNormalizedEol();
        if (!actualText.contains(expectedText)) {
            if (!expectedText.contains("\n")) {
                Arrays.stream(actualText.split("\n"))
                    // Measure Levenshtein distance for each line
                    .map(line -> new LineWithDistance(line, StringUtils.getLevenshteinDistance(line, expectedText)))
                    // Filter out lines that need more edits than half the length of the line
                    .filter(pair -> pair.getDistance() < pair.getLine().length() / 2)
                    // Find the closest match
                    .min(Comparator.comparingInt(LineWithDistance::getDistance))
                    .map(LineWithDistance::getLine)
                    .ifPresent(similarLine -> failOnDifferentLine("Did not find expected text in " + label.toLowerCase() + ", found similar line.", expectedOutput, similarLine));
            }
            failOnMissingOutput("Did not find expected text in " + label.toLowerCase() + ".", label, expectedOutput, actualText);
        }
        return this;
    }

    @Override
    public ExecutionResult assertOutputContains(String expectedOutput) {
        return assertContentContains(getMainContent().withNormalizedEol(), expectedOutput, "Build output");
    }

    @Override
    public boolean hasErrorOutput(String expectedOutput) {
        return getError().contains(expectedOutput);
    }

    @Override
    public ExecutionResult assertHasErrorOutput(String expectedOutput) {
        return assertContentContains(errorContent.withNormalizedEol(), expectedOutput, "Error output");
    }

    @Override
    public String getError() {
        return error.withNormalizedEol();
    }

    @Override
    public String getOutputLineThatContains(String text) {
        return findLineThatContains(text, getMainContent(), "build output.");
    }

    @Override
    public String getPostBuildOutputLineThatContains(String text) {
        return findLineThatContains(text, getPostBuildContent(), "post build output.");
    }

    private String findLineThatContains(String text, LogContent content, String outputType) {
        Optional<String> foundLine = content.getLines().stream()
            .filter(line -> line.contains(text))
            .findFirst();
        return foundLine.orElseGet(() -> {
            failOnMissingOutput("Did not find expected text in " + outputType, "Build output", text, text);
            // never returned
            return "";
        });
    }

    public List<String> getExecutedTasks() {
        return ImmutableList.copyOf(findExecutedTasksInOrderStarted());
    }

    private Set<String> findExecutedTasksInOrderStarted() {
        if (tasks == null) {
            tasks = new LinkedHashSet<>(grepTasks(TASK_PATTERN));
        }
        return tasks;
    }

    @Override
    public ExecutionResult assertTasksExecutedInOrder(Object... taskPaths) {
        Set<String> allTasks = TaskOrderSpecs.exact(taskPaths).getTasks();
        assertTasksExecuted(allTasks);
        assertTaskOrder(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTasksExecuted(Object... taskPaths) {
        Set<String> expectedTasks = new TreeSet<>(flattenTaskPaths(taskPaths));
        Set<String> actualTasks = findExecutedTasksInOrderStarted();
        if (!expectedTasks.equals(actualTasks)) {
            failOnDifferentSets("Build output does not contain the expected tasks.", expectedTasks, actualTasks);
        }
        return this;
    }

    @Override
    public ExecutionResult assertTasksExecutedAndNotSkipped(Object... taskPaths) {
        assertTasksExecuted(taskPaths);
        return assertTasksNotSkipped(taskPaths);
    }

    @Override
    public ExecutionResult assertTaskExecuted(String taskPath) {
        Set<String> actualTasks = findExecutedTasksInOrderStarted();
        if (!actualTasks.contains(taskPath)) {
            failOnMissingElement("Build output does not contain the expected task.", taskPath, actualTasks);
        }
        return this;
    }

    @Override
    public ExecutionResult assertTaskNotExecuted(String taskPath) {
        Set<String> actualTasks = findExecutedTasksInOrderStarted();
        if (actualTasks.contains(taskPath)) {
            failOnMissingElement("Build output does contains unexpected task.", taskPath, actualTasks);
        }
        return this;
    }

    @Override
    public ExecutionResult assertTaskOrder(Object... taskPaths) {
        TaskOrderSpecs.exact(taskPaths).assertMatches(-1, getExecutedTasks());
        return this;
    }

    public Set<String> getSkippedTasks() {
        return new TreeSet<>(grepTasks(SKIPPED_TASK_PATTERN));
    }

    @Override
    public ExecutionResult assertTasksSkipped(Object... taskPaths) {
        Set<String> expectedTasks = new TreeSet<>(flattenTaskPaths(taskPaths));
        Set<String> skippedTasks = getSkippedTasks();
        if (!expectedTasks.equals(skippedTasks)) {
            failOnDifferentSets("Build output does not contain the expected skipped tasks.", expectedTasks, skippedTasks);
        }
        return this;
    }

    @Override
    public ExecutionResult assertTaskSkipped(String taskPath) {
        Set<String> tasks = new TreeSet<>(getSkippedTasks());
        if (!tasks.contains(taskPath)) {
            failOnMissingElement("Build output does not contain the expected skipped task.", taskPath, tasks);
        }
        return this;
    }

    private Collection<String> getNotSkippedTasks() {
        Set<String> all = new TreeSet<>(getExecutedTasks());
        Set<String> skipped = getSkippedTasks();
        all.removeAll(skipped);
        return all;
    }

    @Override
    public ExecutionResult assertTasksNotSkipped(Object... taskPaths) {
        Set<String> expectedTasks = new TreeSet<>(flattenTaskPaths(taskPaths));
        Set<String> tasks = new TreeSet<>(getNotSkippedTasks());
        if (!expectedTasks.equals(tasks)) {
            failOnDifferentSets("Build output does not contain the expected non skipped tasks.", expectedTasks, tasks);
        }
        return this;
    }

    @Override
    public ExecutionResult assertTaskNotSkipped(String taskPath) {
        Set<String> tasks = new TreeSet<>(getNotSkippedTasks());
        if (!tasks.contains(taskPath)) {
            failOnMissingElement("Build output does not contain the expected non skipped task.", taskPath, tasks);
        }
        return this;
    }

    @Override
    public void assertResultVisited() {
    }

    private void failOnDifferentSets(String message, Set<String> expected, Set<String> actual) {
        failureOnUnexpectedOutput(String.format("%s%nExpected: %s%nActual: %s", message, expected, actual));
    }

    private void failOnMissingElement(String message, String expected, Set<String> actual) {
        failureOnUnexpectedOutput(String.format("%s%nExpected: %s%nActual: %s", message, expected, actual));
    }

    private void failOnMissingOutput(String message, String type, String expected, String actual) {
        throw new AssertionError(String.format("%s%nExpected: %s%n%n%s:%n=======%n%s", message, expected, type, actual));
    }

    private void failOnDifferentLine(String message, String expected, String actual) {
        throw new SpockAssertionError(String.format("%nExpected: \"%s\"%n but: was \"%s\"", expected, actual));
    }

    protected void failureOnUnexpectedOutput(String message) {
        throw new AssertionError(unexpectedOutputMessage(message));
    }

    private String unexpectedOutputMessage(String message) {
        return String.format("%s%nOutput:%n=======%n%s%nError:%n======%n%s", message, getOutput(), getError());
    }

    private List<String> grepTasks(final Pattern pattern) {
        final List<String> tasks = Lists.newArrayList();
        final List<String> taskStatusLines = Lists.newArrayList();

        getMainContent().eachLine(line -> {
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String taskStatusLine = matcher.group().replace(TASK_PREFIX, "");
                String taskName = matcher.group(2);
                if (!includeBuildSrc && taskName.startsWith(":buildSrc:")) {
                    return;
                }

                // The task status line may appear twice - once for the execution, once for the UP-TO-DATE/SKIPPED/etc
                // So don't add to the task list if this is an update to a previously added task.

                // Find the status line for the previous record of this task
                String previousTaskStatusLine = tasks.contains(taskName) ? taskStatusLines.get(tasks.lastIndexOf(taskName)) : "";
                // Don't add if our last record has a `:taskName` status, and this one is `:taskName SOMETHING`
                if (previousTaskStatusLine.equals(taskName) && !taskStatusLine.equals(taskName)) {
                    return;
                }

                taskStatusLines.add(taskStatusLine);
                tasks.add(taskName);
            }
        });

        return tasks;
    }
}
