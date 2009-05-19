/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests;

import org.gradle.StartParameter;
import org.gradle.Gradle;
import org.gradle.BuildResult;
import org.gradle.BuildListener;
import org.gradle.execution.BuiltInTasksBuildExecuter;
import org.gradle.api.Task;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.invocation.Build;
import org.gradle.api.initialization.Settings;
import org.hamcrest.Matcher;
import static org.junit.Assert.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;

import junit.framework.AssertionFailedError;

// todo: implement more of the unsupported methods
public class InProcessGradleExecuter implements GradleExecuter {
    private final StartParameter parameter;
    private final List<String> tasks = new ArrayList<String>();
    private final List<Task> planned = new ArrayList<Task>();

    public InProcessGradleExecuter(StartParameter parameter) {
        this.parameter = parameter;
    }

    public GradleExecuter reset() {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter inDirectory(File directory) {
        parameter.setCurrentDir(directory);
        return this;
    }

    public InProcessGradleExecuter withSearchUpwards() {
        parameter.setSearchUpwards(true);
        return this;
    }

    public InProcessGradleExecuter withTasks(String... names) {
        parameter.setTaskNames(Arrays.asList(names));
        return this;
    }

    public InProcessGradleExecuter withTaskList() {
        parameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS));
        return this;
    }

    public InProcessGradleExecuter withDependencyList() {
        parameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.DEPENDENCIES));
        return this;
    }

    public InProcessGradleExecuter usingSettingsFile(TestFile settingsFile) {
        return usingSettingsFile(settingsFile.asFile());
    }

    public InProcessGradleExecuter usingSettingsFile(File settingsFile) {
        parameter.setSettingsFile(settingsFile);
        return this;
    }

    public InProcessGradleExecuter usingBuildScript(String script) {
        parameter.useEmbeddedBuildFile(script);
        return this;
    }

    public GradleExecuter withQuietLogging() {
        parameter.setLogLevel(LogLevel.QUIET);
        return this;
    }

    public GradleExecuter withLogLevel(int logLevel) {
        throw new UnsupportedOperationException();
    }

    public GradleExecuter withArguments(String... args) {
        throw new UnsupportedOperationException();
    }

    public ExecutionResult run() {
        Gradle gradle = Gradle.newInstance(parameter);
        gradle.addBuildListener(new ListenerImpl());
        BuildResult result = gradle.run();
        result.rethrowFailure();
        return new InProcessExecutionResult(tasks);
    }

    public ExecutionFailure runWithFailure() {
        try {
            run();
            throw new AssertionFailedError("expected build to fail.");
        } catch (GradleException e) {
            return new InProcessExecutionFailure(tasks, e);
        }
    }

    private class ListenerImpl implements BuildListener {
        private TaskListenerImpl listener = new TaskListenerImpl();

        public void buildStarted(StartParameter startParameter) {
        }

        public void settingsEvaluated(Settings settings) {
        }

        public void projectsLoaded(Build build) {
        }

        public void projectsEvaluated(Build build) {
        }

        public void taskGraphPopulated(TaskExecutionGraph graph) {
            planned.clear();
            planned.addAll(graph.getAllTasks());
            graph.addTaskExecutionListener(listener);
        }

        public void buildFinished(BuildResult result) {
        }
    }

    private class TaskListenerImpl implements TaskExecutionListener {
        private Task current;

        public void beforeExecute(Task task) {
            assertThat(current, org.hamcrest.Matchers.nullValue());
            assertTrue(planned.contains(task));
            current = task;
        }

        public void afterExecute(Task task, Throwable failure) {
            assertThat(task, org.hamcrest.Matchers.sameInstance(current));
            current = null;
            tasks.add(task.getPath());
        }
    }

    public static class InProcessExecutionResult implements ExecutionResult {
        private final List<String> plannedTasks;

        public InProcessExecutionResult(List<String> plannedTasks) {
            this.plannedTasks = plannedTasks;
        }

        public String getOutput() {
            throw new UnsupportedOperationException();
        }

        public String getError() {
            throw new UnsupportedOperationException();
        }

        public void assertTasksExecuted(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(plannedTasks, org.hamcrest.Matchers.equalTo(expected));
        }
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private final GradleException failure;

        public InProcessExecutionFailure(List<String> tasks, GradleException failure) {
            super(tasks);
            if (failure instanceof GradleScriptException) {
                this.failure = ((GradleScriptException) failure).getReportableException();
            } else {
                this.failure = failure;
            }
        }

        public GradleException getFailure() {
            return failure;
        }

        public void assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), org.hamcrest.Matchers.containsString(String.format(" line: %d", lineNumber)));
        }

        public void assertHasFileName(String filename) {
            assertThat(failure.getMessage(), org.hamcrest.Matchers.startsWith(String.format("%s", filename)));
        }

        public void assertHasDescription(String description) {
            assertThat(failure.getCause().getMessage(), org.hamcrest.Matchers.endsWith(description));
        }

        public void assertDescription(Matcher<String> matcher) {
            assertThat(failure.getCause().getMessage(), matcher);
        }

        public void assertHasContext(String context) {
            assertThat(failure.getMessage(), org.hamcrest.Matchers.containsString(context));
        }

        public void assertContext(Matcher<String> matcher) {
            assertThat(failure.getMessage(), matcher);
        }
    }
}
