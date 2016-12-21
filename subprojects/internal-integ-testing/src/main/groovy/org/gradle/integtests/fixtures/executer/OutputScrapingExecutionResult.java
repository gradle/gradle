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

import com.google.common.io.CharSource;
import org.apache.commons.collections.CollectionUtils;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.launcher.daemon.client.DaemonStartupMessage;
import org.gradle.launcher.daemon.server.DaemonStateCoordinator;
import org.gradle.util.TextUtil;
import org.hamcrest.core.StringContains;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.gradle.util.TextUtil.normaliseLineSeparators;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public class OutputScrapingExecutionResult implements ExecutionResult {
    static final Pattern STACK_TRACE_ELEMENT = Pattern.compile("\\s+(at\\s+)?[\\w.$_]+\\.[\\w$_ =\\+\'-]+\\(.+?\\)");
    private final String output;
    private final String error;

    private static final String TASK_LOGGER_DEBUG_PATTERN = "(?:.*\\s+\\[LIFECYCLE\\]\\s+\\[class org\\.gradle\\.TaskExecutionLogger\\]\\s+)?";

    //for example: ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a'
    private final Pattern skippedTaskPattern = Pattern.compile(TASK_LOGGER_DEBUG_PATTERN + "(:\\S+?(:\\S+?)*)\\s+((SKIPPED)|(UP-TO-DATE)|(FROM-CACHE))");

    //for example: ':hey' or ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a FOO'
    private final Pattern taskPattern = Pattern.compile(TASK_LOGGER_DEBUG_PATTERN + "(:\\S+?(:\\S+?)*)((\\s+SKIPPED)|(\\s+UP-TO-DATE)|(\\s+FROM-CACHE)|(\\s+FAILED)|(\\s*))");

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
            } else if (i == lines.size() - 1 && line.matches("Total time: [\\d\\.]+ secs")) {
                result.append("Total time: 1 secs");
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

    public ExecutionResult assertTasksExecuted(String... taskPaths) {
        List<String> expectedTasks = Arrays.asList(taskPaths);
        assertThat(String.format("Expected tasks %s not found in process output:%n%s", expectedTasks, getOutput()), getExecutedTasks(), equalTo(expectedTasks));
        return this;
    }

    public Set<String> getSkippedTasks() {
        return new HashSet<String>(grepTasks(skippedTaskPattern));
    }

    public ExecutionResult assertTasksSkipped(String... taskPaths) {
        Set<String> expectedTasks = new HashSet<String>(Arrays.asList(taskPaths));
        assertThat(String.format("Expected skipped tasks %s not found in process output:%n%s", expectedTasks, getOutput()), getSkippedTasks(), equalTo(expectedTasks));
        return this;
    }

    public ExecutionResult assertTaskSkipped(String taskPath) {
        Set<String> tasks = new HashSet<String>(getSkippedTasks());
        assertThat(String.format("Expected skipped task %s not found in process output:%n%s", taskPath, getOutput()), tasks, hasItem(taskPath));
        return this;
    }

    public ExecutionResult assertTasksNotSkipped(String... taskPaths) {
        Set<String> tasks = new HashSet<String>(getNotSkippedTasks());
        Set<String> expectedTasks = new HashSet<String>(Arrays.asList(taskPaths));
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
        final LinkedList<String> tasks = new LinkedList<String>();

        eachLine(new Action<String>() {
            public void execute(String s) {
                java.util.regex.Matcher matcher = pattern.matcher(s);
                if (matcher.matches()) {
                    String taskName = matcher.group(1);
                    if (!taskName.contains(":buildSrc:")) {
                        //for INFO/DEBUG level the task may appear twice - once for the execution, once for the UP-TO-DATE
                        //so I'm not adding the task to the list if it is the same as previously added task.
                        if (tasks.size() == 0 || !tasks.getLast().equals(taskName)) {
                            tasks.add(taskName);
                        }
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
