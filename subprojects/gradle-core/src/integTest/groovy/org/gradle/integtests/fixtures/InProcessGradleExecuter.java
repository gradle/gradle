/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.fixtures;

import junit.framework.AssertionFailedError;
import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.LocationAwareException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.CommandLineParser;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.hamcrest.Matcher;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.gradle.util.Matchers.containsLine;
import static org.gradle.util.Matchers.hasMessage;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class InProcessGradleExecuter extends AbstractGradleExecuter {
    private StartParameter parameter;

    public InProcessGradleExecuter(StartParameter parameter) {
        this.parameter = parameter;
    }

    @Override
    public GradleExecuter reset() {
        super.reset();
        parameter = new StartParameter();
        return this;
    }

    public StartParameter getParameter() {
        return parameter;
    }

    @Override
    public GradleExecuter inDirectory(File directory) {
        parameter.setCurrentDir(directory);
        return this;
    }

    @Override
    public InProcessGradleExecuter withSearchUpwards() {
        parameter.setSearchUpwards(true);
        return this;
    }

    @Override
    public GradleExecuter withTasks(List<String> names) {
        parameter.setTaskNames(names);
        return this;
    }

    @Override
    public InProcessGradleExecuter withTaskList() {
        parameter.setTaskNames(toList("tasks"));
        return this;
    }

    @Override
    public InProcessGradleExecuter withDependencyList() {
        parameter.setTaskNames(toList("dependencies"));
        return this;
    }

    @Override
    public InProcessGradleExecuter usingSettingsFile(File settingsFile) {
        parameter.setSettingsFile(settingsFile);
        return this;
    }

    @Override
    public GradleExecuter usingInitScript(File initScript) {
        parameter.addInitScript(initScript);
        return this;
    }

    @Override
    public GradleExecuter usingBuildScript(File buildScript) {
        parameter.setBuildFile(buildScript);
        return this;
    }

    @Override
    public GradleExecuter usingBuildScript(String scriptText) {
        parameter.useEmbeddedBuildFile(scriptText);
        return this;
    }

    @Override
    public GradleExecuter withArguments(List<String> args) {
        CommandLineParser parser = new CommandLineParser();
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        converter.configure(parser);
        converter.convert(parser.parse(args), parameter);
        return this;
    }

    @Override
    public GradleExecuter withUserHomeDir(File userHomeDir) {
        parameter.setGradleUserHomeDir(userHomeDir);
        return this;
    }

    @Override
    protected ExecutionResult doRun() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputListener, errorListener, buildListener);
        result.rethrowFailure();
        return new InProcessExecutionResult(buildListener.executedTasks, buildListener.skippedTasks,
                outputListener.toString(), errorListener.toString());
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        try {
            doRun(outputListener, errorListener, buildListener).rethrowFailure();
            throw new AssertionFailedError("expected build to fail but it did not.");
        } catch (GradleException e) {
            return new InProcessExecutionFailure(buildListener.executedTasks, buildListener.skippedTasks,
                    outputListener.writer.toString(), errorListener.writer.toString(), e);
        }
    }

    private BuildResult doRun(final OutputListenerImpl outputListener, OutputListenerImpl errorListener,
                              BuildListenerImpl listener) {
        assertCanExecute();
        if (isQuiet()) {
            parameter.setLogLevel(LogLevel.QUIET);
        }
        GradleLauncher gradleLauncher = GradleLauncher.newInstance(parameter);
        gradleLauncher.addListener(listener);
        gradleLauncher.addStandardOutputListener(outputListener);
        gradleLauncher.addStandardErrorListener(errorListener);
        try {
            return gradleLauncher.run();
        } finally {
            System.clearProperty("test.single");
        }
    }

    public void assertCanExecute() {
        assertNull(getExecutable());
        assertTrue(getEnvironmentVars().isEmpty());
    }

    public boolean canExecute() {
        try {
            assertCanExecute();
        } catch (AssertionError e) {
            return false;
        }
        return true;
    }

    private static class BuildListenerImpl implements TaskExecutionGraphListener {
        private final List<String> executedTasks = new ArrayList<String>();
        private final List<String> skippedTasks = new ArrayList<String>();

        public void graphPopulated(TaskExecutionGraph graph) {
            List<Task> planned = new ArrayList<Task>(graph.getAllTasks());
            graph.addTaskExecutionListener(new TaskListenerImpl(planned, executedTasks, skippedTasks));
        }
    }

    private static class OutputListenerImpl implements StandardOutputListener {
        private StringWriter writer = new StringWriter();

        @Override
        public String toString() {
            return writer.toString();
        }

        public void onOutput(CharSequence output) {
            writer.append(output);
        }
    }

    private static class TaskListenerImpl implements TaskExecutionListener {
        private final List<Task> planned;
        private final List<String> executedTasks;
        private final List<String> skippedTasks;
        private Task current;

        public TaskListenerImpl(List<Task> planned, List<String> executedTasks, List<String> skippedTasks) {
            this.planned = planned;
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
        }

        public void beforeExecute(Task task) {
            assertThat(current, nullValue());
            assertTrue(planned.contains(task));
            current = task;
        }

        public void afterExecute(Task task, TaskState state) {
            assertThat(task, sameInstance(current));
            current = null;
            executedTasks.add(task.getPath());
            if (state.getSkipped()) {
                skippedTasks.add(task.getPath());
            }
        }
    }

    public static class InProcessExecutionResult extends AbstractExecutionResult {
        private final List<String> plannedTasks;
        private final List<String> skippedTasks;
        private final String output;
        private final String error;

        public InProcessExecutionResult(List<String> plannedTasks, List<String> skippedTasks, String output,
                                        String error) {
            this.plannedTasks = plannedTasks;
            this.skippedTasks = skippedTasks;
            this.output = output;
            this.error = error;
        }

        public String getOutput() {
            return output;
        }

        public String getError() {
            return error;
        }

        public ExecutionResult assertTasksExecuted(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(plannedTasks, equalTo(expected));
            return this;
        }

        public ExecutionResult assertTasksSkipped(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(skippedTasks, equalTo(expected));
            return this;
        }

        public ExecutionResult assertTasksNotSkipped(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            List<String> notSkipped = new ArrayList<String>(plannedTasks);
            notSkipped.removeAll(skippedTasks);
            assertThat(notSkipped, equalTo(expected));
            return this;
        }
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private final GradleException failure;

        public InProcessExecutionFailure(List<String> tasks, List<String> skippedTasks, String output, String error,
                                         GradleException failure) {
            super(tasks, skippedTasks, output, error);
            this.failure = failure;
        }

        public ExecutionFailure assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), containsString(String.format(" line: %d", lineNumber)));
            return this;

        }

        public ExecutionFailure assertHasFileName(String filename) {
            assertThat(failure.getMessage(), startsWith(String.format("%s", filename)));
            return this;
        }

        public ExecutionFailure assertHasCause(String description) {
            assertThatCause(startsWith(description));
            return this;
        }

        public ExecutionFailure assertThatCause(final Matcher<String> matcher) {
            if (failure instanceof LocationAwareException) {
                LocationAwareException exception = (LocationAwareException) failure;
                assertThat(exception.getReportableCauses(), hasItem(hasMessage(matcher)));
            } else {
                assertThat(failure.getCause(), notNullValue());
                assertThat(failure.getCause().getMessage(), matcher);
            }
            return this;
        }

        public ExecutionFailure assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
            return this;
        }

        public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
            assertThat(failure.getMessage(), containsLine(matcher));
            return this;
        }
    }
}
