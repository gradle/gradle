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

package org.gradle.integtests.fixtures.executer;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.LocationAwareException;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.cli.CommandLineParser;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.launcher.Main;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.util.DeprecationLogger;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.util.Arrays.asList;
import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

class InProcessGradleExecuter extends AbstractGradleExecuter {

    private final ProcessEnvironment processEnvironment = NativeServices.getInstance().get(ProcessEnvironment.class);

    InProcessGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    @Override
    public GradleExecuter reset() {
        DeprecationLogger.reset();
        return super.reset();
    }

    @Override
    protected ExecutionResult doRun() {
        OutputListenerImpl outputListener = new OutputListenerImpl();
        OutputListenerImpl errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputListener, errorListener, buildListener);
        try {
            result.rethrowFailure();
        } catch (Exception e) {
            throw new UnexpectedBuildFailure(e);
        }
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
            throw new AssertionError("expected build to fail but it did not.");
        } catch (GradleException e) {
            return new InProcessExecutionFailure(buildListener.executedTasks, buildListener.skippedTasks,
                    outputListener.writer.toString(), errorListener.writer.toString(), e);
        }
    }

    @Override
    protected GradleHandle doStart() {
        return new ForkingGradleHandle(getDefaultCharacterEncoding(), new Factory<JavaExecHandleBuilder>() {
            public JavaExecHandleBuilder create() {
                JavaExecHandleBuilder builder = new JavaExecHandleBuilder(new IdentityFileResolver());
                builder.workingDir(getWorkingDir());
                Set<File> classpath = new DefaultModuleRegistry().getFullClasspath();
                builder.classpath(classpath);
                builder.setMain(Main.class.getName());
                builder.args(getAllArgs());
                return builder;
            }
        }).start();
    }

    private BuildResult doRun(final OutputListenerImpl outputListener, OutputListenerImpl errorListener, BuildListenerImpl listener) {
        // Capture the current state of things that we will change during execution
        InputStream originalStdIn = System.in;
        Properties originalSysProperties = new Properties();
        originalSysProperties.putAll(System.getProperties());
        File originalUserDir = new File(originalSysProperties.getProperty("user.dir"));
        Map<String, String> originalEnv = System.getenv();

        // Augment the environment for the execution
        System.setIn(getStdin());
        processEnvironment.maybeSetProcessDir(getWorkingDir());
        for (Map.Entry<String, String> entry : getEnvironmentVars().entrySet()) {
            processEnvironment.maybeSetEnvironmentVariable(entry.getKey(), entry.getValue());
        }
        Map<String, String> implicitJvmSystemProperties = getImplicitJvmSystemProperties();
        System.getProperties().putAll(implicitJvmSystemProperties);

        StartParameter parameter = new StartParameter();
        parameter.setLogLevel(LogLevel.INFO);
        parameter.setSearchUpwards(true);
        parameter.setCurrentDir(getWorkingDir());

        CommandLineParser parser = new CommandLineParser();
        DefaultCommandLineConverter converter = new DefaultCommandLineConverter();
        converter.configure(parser);
        converter.convert(parser.parse(getAllArgs()), parameter);

        DefaultGradleLauncherFactory factory = (DefaultGradleLauncherFactory) GradleLauncher.getFactory();
        factory.addListener(listener);
        GradleLauncher gradleLauncher = GradleLauncher.newInstance(parameter);
        gradleLauncher.addStandardOutputListener(outputListener);
        gradleLauncher.addStandardErrorListener(errorListener);
        try {
            return gradleLauncher.run();
        } finally {
            // Restore the environment
            System.setProperties(originalSysProperties);
            processEnvironment.maybeSetProcessDir(originalUserDir);
            for (Map.Entry<String, String> entry : originalEnv.entrySet()) {
                String oldValue = entry.getValue();
                if (oldValue != null) {
                    processEnvironment.maybeSetEnvironmentVariable(entry.getKey(), oldValue);
                } else {
                    processEnvironment.maybeRemoveEnvironmentVariable(entry.getKey());
                }
            }
            factory.removeListener(listener);
            System.setIn(originalStdIn);
        }
    }

    public DaemonRegistry getDaemonRegistry() {
        throw new UnsupportedOperationException();
    }

    public void assertCanExecute() {
        assertNull(getExecutable());
        assertEquals(getJavaHome(), Jvm.current().getJavaHome());
        assertEquals(getDefaultCharacterEncoding(), Charset.defaultCharset().name());
        assertFalse(isRequireGradleHome());
    }

    private static class BuildListenerImpl implements TaskExecutionGraphListener, BuildListener {
        private final List<String> executedTasks = new CopyOnWriteArrayList<String>();
        private final Set<String> skippedTasks = new CopyOnWriteArraySet<String>();

        public void graphPopulated(TaskExecutionGraph graph) {
            List<Task> planned = new ArrayList<Task>(graph.getAllTasks());
            graph.addTaskExecutionListener(new TaskListenerImpl(planned, executedTasks, skippedTasks));
        }

        public void buildStarted(Gradle gradle) {
            DeprecationLogger.setLogTrace(true);
        }

        public void settingsEvaluated(Settings settings) {

        }

        public void projectsLoaded(Gradle gradle) {

        }

        public void projectsEvaluated(Gradle gradle) {

        }

        public void buildFinished(BuildResult result) {
            DeprecationLogger.setLogTrace(false);
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
        private final Set<String> skippedTasks;

        public TaskListenerImpl(List<Task> planned, List<String> executedTasks, Set<String> skippedTasks) {
            this.planned = planned;
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
        }

        public void beforeExecute(Task task) {
            assertTrue(planned.contains(task));

            String taskPath = path(task);
            if (taskPath.startsWith(":buildSrc:")) {
                return;
            }

            executedTasks.add(taskPath);
        }

        public void afterExecute(Task task, TaskState state) {
            String taskPath = path(task);
            if (taskPath.startsWith(":buildSrc:")) {
                return;
            }

            if (state.getSkipped()) {
                skippedTasks.add(taskPath);
            }
        }

        private String path(Task task) {
            return task.getProject().getGradle().getParent() == null ? task.getPath() : ":" + task.getProject().getRootProject().getName() + task.getPath();
        }
    }

    public static class InProcessExecutionResult implements ExecutionResult {
        private final List<String> plannedTasks;
        private final Set<String> skippedTasks;
        private final String output;
        private final String error;

        public InProcessExecutionResult(List<String> plannedTasks, Set<String> skippedTasks, String output,
                                        String error) {
            this.plannedTasks = plannedTasks;
            this.skippedTasks = skippedTasks;
            this.output = output;
            this.error = error;
        }

        public String getOutput() {
            return output;
        }

        public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines) {
            new SequentialOutputMatcher().assertOutputMatches(expectedOutput, getOutput(), ignoreExtraLines);
            return this;
        }

        public String getError() {
            return error;
        }

        public List<String> getExecutedTasks() {
            return new ArrayList<String>(plannedTasks);
        }

        public ExecutionResult assertTasksExecuted(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(plannedTasks, equalTo(expected));
            return this;
        }

        public ExecutionResult assertProjectsEvaluated(String... projectPaths) {
            new OutputScraper(getOutput()).assertProjectsEvaluated(asList(projectPaths));
            return this;
        }

        public Set<String> getSkippedTasks() {
            return new HashSet<String>(skippedTasks);
        }

        public ExecutionResult assertTasksSkipped(String... taskPaths) {
            Set<String> expected = new HashSet<String>(Arrays.asList(taskPaths));
            assertThat(skippedTasks, equalTo(expected));
            return this;
        }

        public ExecutionResult assertTaskSkipped(String taskPath) {
            assertThat(skippedTasks, hasItem(taskPath));
            return this;
        }

        public ExecutionResult assertTasksNotSkipped(String... taskPaths) {
            Set<String> expected = new HashSet<String>(Arrays.asList(taskPaths));
            Set<String> notSkipped = getNotSkippedTasks();
            assertThat(notSkipped, equalTo(expected));
            return this;
        }

        public ExecutionResult assertTaskNotSkipped(String taskPath) {
            assertThat(getNotSkippedTasks(), hasItem(taskPath));
            return this;
        }

        private Set<String> getNotSkippedTasks() {
            Set<String> notSkipped = new HashSet<String>(plannedTasks);
            notSkipped.removeAll(skippedTasks);
            return notSkipped;
        }
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private final GradleException failure;

        public InProcessExecutionFailure(List<String> tasks, Set<String> skippedTasks, String output, String error,
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
            List<Throwable> causes = new ArrayList<Throwable>();
            extractCauses(failure, causes);
            assertThat(causes, Matchers.<Throwable>hasItem(hasMessage(matcher)));
            return this;
        }

        private void extractCauses(Throwable failure, List<Throwable> causes) {
            if (failure instanceof MultipleBuildFailures) {
                MultipleBuildFailures exception = (MultipleBuildFailures) failure;
                for (Throwable componentFailure : exception.getCauses()) {
                    extractCauses(componentFailure, causes);
                }
            } else if (failure instanceof LocationAwareException) {
                causes.addAll(((LocationAwareException) failure).getReportableCauses());
            } else {
                causes.add(failure.getCause());
            }
        }

        public ExecutionFailure assertHasNoCause() {
            if (failure instanceof LocationAwareException) {
                LocationAwareException exception = (LocationAwareException) failure;
                assertThat(exception.getReportableCauses(), isEmpty());
            } else {
                assertThat(failure.getCause(), nullValue());
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

        public ExecutionFailure assertTestsFailed() {
            new DetailedExecutionFailure(this).assertTestsFailed();
            return this;
        }

        public DependencyResolutionFailure getDependencyResolutionFailure() {
            return new DependencyResolutionFailure(this);
        }
    }
}
