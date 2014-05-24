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

import org.apache.commons.collections.CollectionUtils;
import org.gradle.api.Action;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class OutputScrapingExecutionResult implements ExecutionResult {
    private final String output;
    private final String error;

    //for example: ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a'
    private final Pattern skippedTaskPattern = Pattern.compile("(:\\S+?(:\\S+?)*)\\s+((SKIPPED)|(UP-TO-DATE))");

    //for example: ':hey' or ':a SKIPPED' or ':foo:bar:baz UP-TO-DATE' but not ':a FOO'
    private final Pattern taskPattern = Pattern.compile("(:\\S+?(:\\S+?)*)((\\s+SKIPPED)|(\\s+UP-TO-DATE)|(\\s+FAILED)|(\\s*))");

    public OutputScrapingExecutionResult(String output, String error) {
        this.output = output;
        this.error = error;
    }

    public String getOutput() {
        return output;
    }

    public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
        SequentialOutputMatcher matcher = ignoreLineOrder ? new AnyOrderOutputMatcher() : new SequentialOutputMatcher();
        matcher.assertOutputMatches(expectedOutput, getOutput(), ignoreExtraLines);
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

    public ExecutionResult assertTaskNotExecuted(String taskPath) {
        Set<String> tasks = new HashSet<String>(getExecutedTasks());
        assertThat(String.format("Expected task %s found in process output:%n%s", taskPath, getOutput()), tasks, not(hasItem(taskPath)));
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
                    if (!taskName.startsWith(":buildSrc:")) {
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
