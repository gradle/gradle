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

import junit.framework.AssertionFailedError;
import org.gradle.*;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.execution.BuiltInTasksBuildExecuter;
import static org.gradle.util.Matchers.*;

import org.gradle.initialization.DefaultCommandLine2StartParameterConverter;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InProcessGradleExecuter extends AbstractGradleExecuter {
    private final StartParameter parameter;

    public InProcessGradleExecuter(StartParameter parameter) {
        this.parameter = parameter;
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
        parameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS));
        return this;
    }

    @Override
    public InProcessGradleExecuter withDependencyList() {
        parameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.DEPENDENCIES));
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
    public GradleExecuter usingBuildScript(String script) {
        parameter.useEmbeddedBuildFile(script);
        return this;
    }

    @Override
    public GradleExecuter withArguments(List<String> args) {
        new DefaultCommandLine2StartParameterConverter().convert(args.toArray(new String[args.size()]), parameter);
        return this;
    }

    public ExecutionResult run() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputListener, errorListener, buildListener);
        result.rethrowFailure();
        return new InProcessExecutionResult(buildListener.executedTasks, buildListener.skippedTasks,
                outputListener.toString(), errorListener.toString());
    }

    public ExecutionFailure runWithFailure() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        try {
            doRun(outputListener, errorListener, buildListener).rethrowFailure();
            throw new AssertionFailedError("expected build to fail.");
        } catch (GradleException e) {
            return new InProcessExecutionFailure(buildListener.executedTasks, buildListener.skippedTasks,
                    outputListener.writer.toString(), errorListener.writer.toString(), e);
        }
    }

    private BuildResult doRun(OutputListenerImpl outputListener, OutputListenerImpl errorListener,
                              BuildListenerImpl listener) {
        if (isQuiet()) {
            parameter.setLogLevel(LogLevel.QUIET);
        }
        GradleLauncher gradleLauncher = GradleLauncher.newInstance(parameter);
        gradleLauncher.addListener(listener);
        gradleLauncher.addStandardOutputListener(outputListener);
        gradleLauncher.addStandardErrorListener(errorListener);
        return gradleLauncher.run();
    }

    private class BuildListenerImpl implements TaskExecutionGraphListener {
        private final List<String> executedTasks = new ArrayList<String>();
        private final List<String> skippedTasks = new ArrayList<String>();

        public void graphPopulated(TaskExecutionGraph graph) {
            List<Task> planned = new ArrayList<Task>(graph.getAllTasks());
            graph.addTaskExecutionListener(new TaskListenerImpl(planned, executedTasks, skippedTasks));
        }
    }

    private class OutputListenerImpl implements StandardOutputListener {
        private StringWriter writer = new StringWriter();

        @Override
        public String toString() {
            return writer.toString();
        }

        public void onOutput(CharSequence output) {
            writer.append(output);
        }
    }

    private class TaskListenerImpl implements TaskExecutionListener {
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

        public void afterExecute(Task task, TaskExecutionResult result) {
            assertThat(task, sameInstance(current));
            current = null;
            executedTasks.add(task.getPath());
            if (result.getSkipMessage() != null) {
                skippedTasks.add(task.getPath());
            }
        }
    }

    public static class InProcessExecutionResult implements ExecutionResult {
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
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private final GradleException failure;

        public InProcessExecutionFailure(List<String> tasks, List<String> skippedTasks, String output, String error,
                                         GradleException failure) {
            super(tasks, skippedTasks, output, error);
            if (failure instanceof GradleScriptException) {
                this.failure = ((GradleScriptException) failure).getReportableException();
            } else {
                this.failure = failure;
            }
        }

        public void assertHasLineNumber(int lineNumber) {
            assertThat(failure.getMessage(), containsString(String.format(" line: %d", lineNumber)));
        }

        public void assertHasFileName(String filename) {
            assertThat(failure.getMessage(), startsWith(String.format("%s", filename)));
        }

        public void assertHasCause(String description) {
            assertThatCause(startsWith(description));
        }

        public void assertThatCause(final Matcher<String> matcher) {
            if (failure instanceof GradleScriptException) {
                GradleScriptException exception = (GradleScriptException) failure;
                assertThat(exception.getReportableCauses(), hasItem(hasMessage(matcher)));
            } else {
                assertThat(failure.getCause().getMessage(), matcher);
            }
        }

        public void assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
        }

        public void assertThatDescription(Matcher<String> matcher) {
            assertThat(failure.getMessage(), containsLine(matcher));
        }
    }
}
