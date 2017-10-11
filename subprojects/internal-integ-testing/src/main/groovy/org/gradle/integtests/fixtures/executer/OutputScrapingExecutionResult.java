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

import com.google.common.collect.Lists;
import com.google.common.io.CharSource;
import org.apache.commons.collections.CollectionUtils;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.integtests.fixtures.logging.GroupedOutputFixture;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.launcher.daemon.client.DaemonStartupMessage;
import org.gradle.launcher.daemon.server.DaemonStateCoordinator;
import org.gradle.launcher.daemon.server.health.LowTenuredSpaceDaemonExpirationStrategy;
import org.gradle.util.GUtil;
import org.gradle.util.TextUtil;
import org.hamcrest.core.StringContains;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.gradle.util.TextUtil.normaliseLineSeparators;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class OutputScrapingExecutionResult implements ExecutionResult {
    static final Pattern STACK_TRACE_ELEMENT = Pattern.compile("\\s+(at\\s+)?([\\w.$_]+/)?[\\w.$_]+\\.[\\w$_ =\\+\'-<>]+\\(.+?\\)(\\x1B\\[0K)?");
    private final String output;
    private final String error;

    private static final String TASK_LOGGER_DEBUG_PATTERN = "(?:.*\\s+\\[LIFECYCLE\\]\\s+\\[class org\\.gradle\\.TaskExecutionLogger\\]\\s+)?";

    //for example: ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a'
    private final Pattern skippedTaskPattern = Pattern.compile(TASK_LOGGER_DEBUG_PATTERN + "(:\\S+?(:\\S+?)*)\\s+((SKIPPED)|(UP-TO-DATE)|(NO-SOURCE)|(FROM-CACHE))");

    //for example: ':hey' or ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a FOO'
    private final Pattern taskPattern = Pattern.compile(TASK_LOGGER_DEBUG_PATTERN + "(:\\S+?(:\\S+?)*)((\\s+SKIPPED)|(\\s+UP-TO-DATE)|(\\s+FROM-CACHE)|(\\s+NO-SOURCE)|(\\s+FAILED)|(\\s*))");

    private static final Pattern BUILD_RESULT_PATTERN = Pattern.compile("BUILD (SUCCESSFUL|FAILED)( \\d+[smh])+");

    public static List<String> flattenTaskPaths(Object[] taskPaths) {
        return org.gradle.util.CollectionUtils.toStringList(GUtil.flatten(taskPaths, Lists.newArrayList()));
    }

    public OutputScrapingExecutionResult(String output, String error) {
        this.output = TextUtil.normaliseLineSeparators(output);
        this.error = TextUtil.normaliseLineSeparators(error);
    }

    public String getOutput() {
        return output;
    }

    @Override
    public String getNormalizedOutput() {
        return normalize(output);
    }

    GroupedOutputFixture groupedOutputFixture;

    @Override
    public GroupedOutputFixture getGroupedOutput() {
        if (groupedOutputFixture == null) {
            groupedOutputFixture = new GroupedOutputFixture(getOutput());
        }
        return groupedOutputFixture;
    }

    public static String normalize(String output) {
        StringBuilder result = new StringBuilder();
        List<String> lines;
        try {
            lines = CharSource.wrap(output).readLines();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.contains(DaemonStartupMessage.STARTING_DAEMON_MESSAGE)) {
                // Remove the "daemon starting" message
                i++;
            } else if (line.contains(DaemonStateCoordinator.DAEMON_WILL_STOP_MESSAGE)) {
                // Remove the "Daemon will be shut down" message
                i++;
            } else if (line.contains(LowTenuredSpaceDaemonExpirationStrategy.EXPIRE_DAEMON_MESSAGE)) {
                // Remove the "Expiring Daemon" message
                i++;
            } else if (line.contains(UnsupportedJavaRuntimeException.JAVA7_DEPRECATION_WARNING)) {
                // Remove the Java 7 deprecation warning. This should be removed after 5.0
                i++;
                while (i < lines.size() && STACK_TRACE_ELEMENT.matcher(lines.get(i)).matches()) {
                    i++;
                }
            } else if (i == lines.size() - 1 && BUILD_RESULT_PATTERN.matcher(line).matches()) {
                result.append(BUILD_RESULT_PATTERN.matcher(line).replaceFirst("BUILD $1 in 0s"));
                result.append('\n');
                i++;
            } else {
                result.append(line);
                result.append('\n');
                i++;
            }
        }

        return result.toString();
    }

    public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
        SequentialOutputMatcher matcher = ignoreLineOrder ? new AnyOrderOutputMatcher() : new SequentialOutputMatcher();
        matcher.assertOutputMatches(expectedOutput, getNormalizedOutput(), ignoreExtraLines);
        return this;
    }

    @Override
    public ExecutionResult assertOutputContains(String expectedOutput) {
        assertThat("Substring not found in build output", getOutput(), StringContains.containsString(normaliseLineSeparators(expectedOutput)));
        return this;
    }

    public String getError() {
        return error;
    }

    public List<String> getExecutedTasks() {
        return grepTasks(taskPattern);
    }

    public ExecutionResult assertTasksExecutedInOrder(Object... taskPaths) {
        Set<String> allTasks = TaskOrderSpecs.exact(taskPaths).getTasks();
        assertTasksExecuted(allTasks);
        assertTaskOrder(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTasksExecuted(Object... taskPaths) {
        List<String> expectedTasks = flattenTaskPaths(taskPaths);
        assertThat(String.format("Expected tasks %s not found in process output:%n%s", expectedTasks, getOutput()), getExecutedTasks(), containsInAnyOrder(expectedTasks.toArray()));
        return this;
    }

    @Override
    public ExecutionResult assertTaskOrder(Object... taskPaths) {
        TaskOrderSpecs.exact(taskPaths).assertMatches(-1, getExecutedTasks());
        return this;
    }

    public Set<String> getSkippedTasks() {
        return new HashSet<String>(grepTasks(skippedTaskPattern));
    }

    @Override
    public ExecutionResult assertTasksSkipped(Object... taskPaths) {
        Set<String> expectedTasks = new HashSet<String>(flattenTaskPaths(taskPaths));
        assertThat(String.format("Expected skipped tasks %s not found in process output:%n%s", expectedTasks, getOutput()), getSkippedTasks(), equalTo(expectedTasks));
        return this;
    }

    public ExecutionResult assertTaskSkipped(String taskPath) {
        Set<String> tasks = new HashSet<String>(getSkippedTasks());
        assertThat(String.format("Expected skipped task %s not found in process output:%n%s", taskPath, getOutput()), tasks, hasItem(taskPath));
        return this;
    }

    @Override
    public ExecutionResult assertTasksNotSkipped(Object... taskPaths) {
        Set<String> tasks = new HashSet<String>(getNotSkippedTasks());
        Set<String> expectedTasks = new HashSet<String>(flattenTaskPaths(taskPaths));
        assertThat(String.format("Expected executed tasks %s not found in process output:%n%s", expectedTasks, getOutput()), tasks, equalTo(expectedTasks));
        return this;
    }

    private Collection<String> getNotSkippedTasks() {
        List all = getExecutedTasks();
        Set skipped = getSkippedTasks();
        return CollectionUtils.subtract(all, skipped);
    }

    public ExecutionResult assertTaskNotSkipped(String taskPath) {
        Set<String> tasks = new HashSet<String>(getNotSkippedTasks());
        assertThat(String.format("Expected executed task %s not found in process output:%n%s", taskPath, getOutput()), tasks, hasItem(taskPath));
        return this;
    }

    private List<String> grepTasks(final Pattern pattern) {
        final List<String> tasks = Lists.newArrayList();
        final List<String> taskStatusLines = Lists.newArrayList();

        eachLine(new Action<String>() {
            public void execute(String s) {
                java.util.regex.Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String taskStatusLine = matcher.group();
                    String taskName = matcher.group(1);
                    if (!taskName.contains(":buildSrc:")) {
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
                }
            }
        });

        return tasks;
    }

    private void eachLine(Action<String> action) {
        BufferedReader reader = new BufferedReader(new StringReader(getOutput()));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                action.execute(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
