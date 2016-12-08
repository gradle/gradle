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

import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.tasks.TaskState;
import org.gradle.cli.CommandLineParser;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.launcher.Main;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.cli.Parameters;
import org.gradle.launcher.cli.ParametersConverter;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.SetSystemProperties;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class InProcessGradleExecuter extends AbstractGradleExecuter {
    private final ProcessEnvironment processEnvironment = GLOBAL_SERVICES.get(ProcessEnvironment.class);

    public static final TestFile COMMON_TMP = new TestFile(new File("build/tmp"));

    static {
        LoggingManagerInternal loggingManager = GLOBAL_SERVICES.getFactory(LoggingManagerInternal.class).create();
        loggingManager.start();
    }

    public InProcessGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    public InProcessGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext);
    }

    @Override
    public GradleExecuter reset() {
        DeprecationLogger.reset();
        return super.reset();
    }

    @Override
    protected ExecutionResult doRun() {
        if (isForkRequired()) {
            return doStart().waitForFinish();
        }

        StandardOutputListener outputListener = new OutputListenerImpl();
        StandardOutputListener errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputListener, errorListener, buildListener);
        try {
            result.rethrowFailure();
        } catch (Exception e) {
            throw new UnexpectedBuildFailure(e);
        }
        return assertResult(new InProcessExecutionResult(buildListener.executedTasks, buildListener.skippedTasks,
                new OutputScrapingExecutionResult(outputListener.toString(), errorListener.toString())));
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        if (isForkRequired()) {
            return doStart().waitForFailure();
        }

        StandardOutputListener outputListener = new OutputListenerImpl();
        StandardOutputListener errorListener = new OutputListenerImpl();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        try {
            doRun(outputListener, errorListener, buildListener).rethrowFailure();
            throw new AssertionError("expected build to fail but it did not.");
        } catch (GradleException e) {
            return assertResult(new InProcessExecutionFailure(buildListener.executedTasks, buildListener.skippedTasks,
                    new OutputScrapingExecutionFailure(outputListener.toString(), errorListener.toString()), e));
        }
    }

    private boolean isForkRequired() {
        if (isUseDaemon() || !getJavaHome().equals(Jvm.current().getJavaHome())) {
            return true;
        }
        File gradleProperties = new File(getWorkingDir(), "gradle.properties");
        if (gradleProperties.isFile()) {
            Properties properties = GUtil.loadProperties(gradleProperties);
            if (properties.getProperty("org.gradle.java.home") != null || properties.getProperty("org.gradle.jvmargs") != null) {
                return true;
            }
        }
        return false;

    }

    private <T extends ExecutionResult> T assertResult(T result) {
        getResultAssertion().execute(result);
        return result;
    }

    @Override
    protected GradleHandle doStart() {
        return new ForkingGradleHandle(getStdinPipe(), isUseDaemon(), getResultAssertion(), getDefaultCharacterEncoding(), getJavaExecBuilder(), getDurationMeasurement()).start();
    }

    private Factory<JavaExecHandleBuilder> getJavaExecBuilder() {
        return new Factory<JavaExecHandleBuilder>() {
            public JavaExecHandleBuilder create() {
                GradleInvocation invocation = buildInvocation();
                JavaExecHandleBuilder builder = new JavaExecHandleBuilder(TestFiles.resolver());
                builder.workingDir(getWorkingDir());
                builder.setExecutable(new File(getJavaHome(), "bin/java"));
                Collection<File> classpath = cleanup(GLOBAL_SERVICES.get(ModuleRegistry.class).getAdditionalClassPath().getAsFiles());
                builder.classpath(classpath);
                builder.jvmArgs(invocation.launcherJvmArgs);

                builder.setMain(Main.class.getName());
                builder.args(invocation.args);
                builder.setStandardInput(connectStdIn());

                return builder;
            }
        };
    }

    private Collection<File> cleanup(List<File> files) {
        List<File> result = new LinkedList<File>();
        String prefix = Jvm.current().getJavaHome().getPath() + File.separator;
        for (File file : files) {
            if (file.getPath().startsWith(prefix)) {
                // IDEA adds the JDK's bootstrap classpath to the classpath it uses to run test - remove this
                continue;
            }
            result.add(file);
        }
        return result;
    }

    private BuildResult doRun(StandardOutputListener outputListener, StandardOutputListener errorListener, BuildListenerImpl listener) {
        // Capture the current state of things that we will change during execution
        InputStream originalStdIn = System.in;
        Properties originalSysProperties = new Properties();
        originalSysProperties.putAll(System.getProperties());
        File originalUserDir = new File(originalSysProperties.getProperty("user.dir")).getAbsoluteFile();
        Map<String, String> originalEnv = new HashMap<String, String>(System.getenv());

        GradleInvocation invocation = buildInvocation();
        Set<String> changedEnvVars = new HashSet<String>(invocation.environmentVars.keySet());

        try {
            return executeBuild(invocation, outputListener, errorListener, listener);
        } finally {
            // Restore the environment
            System.setProperties(originalSysProperties);
            resetTempDirLocation();
            processEnvironment.maybeSetProcessDir(originalUserDir);
            for (String envVar : changedEnvVars) {
                String oldValue = originalEnv.get(envVar);
                if (oldValue != null) {
                    processEnvironment.maybeSetEnvironmentVariable(envVar, oldValue);
                } else {
                    processEnvironment.maybeRemoveEnvironmentVariable(envVar);
                }
            }
            System.setProperty("user.dir", originalSysProperties.getProperty("user.dir"));
            System.setIn(originalStdIn);
        }
    }

    private void resetTempDirLocation() {
        SetSystemProperties.resetTempDirLocation();
    }

    private BuildResult executeBuild(GradleInvocation invocation, StandardOutputListener outputListener, StandardOutputListener errorListener, BuildListenerImpl listener) {
        // Augment the environment for the execution
        System.setIn(connectStdIn());
        processEnvironment.maybeSetProcessDir(getWorkingDir());
        for (Map.Entry<String, String> entry : invocation.environmentVars.entrySet()) {
            processEnvironment.maybeSetEnvironmentVariable(entry.getKey(), entry.getValue());
        }
        Map<String, String> implicitJvmSystemProperties = getImplicitJvmSystemProperties();
        System.getProperties().putAll(implicitJvmSystemProperties);
        resetTempDirLocation();

        // TODO: Fix tests that rely on this being set before we process arguments like this...
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(getWorkingDir());
        startParameter.setShowStacktrace(ShowStacktrace.ALWAYS);

        CommandLineParser parser = new CommandLineParser();
        ParametersConverter parametersConverter = new ParametersConverter();
        parametersConverter.configure(parser);
        final Parameters parameters = new Parameters(startParameter);
        parametersConverter.convert(parser.parse(getAllArgs()), parameters);
        if (parameters.getDaemonParameters().isStop()) {
            // --stop should simulate stopping the daemon
            cleanupCachedClassLoaders();
            GLOBAL_SERVICES.get(InMemoryTaskArtifactCache.class).invalidateAll();
        }

        BuildActionExecuter<BuildActionParameters> actionExecuter = GLOBAL_SERVICES.get(BuildActionExecuter.class);

        ListenerManager listenerManager = GLOBAL_SERVICES.get(ListenerManager.class);
        listenerManager.addListener(listener);

        try {
            // TODO: Reuse more of BuildActionsFactory
            BuildAction action = new ExecuteBuildAction(startParameter);
            BuildActionParameters buildActionParameters = createBuildActionParameters(startParameter);
            BuildRequestContext buildRequestContext = createBuildRequestContext(outputListener, errorListener);
            startMeasurement();
            actionExecuter.execute(action, buildRequestContext, buildActionParameters, GLOBAL_SERVICES);
            return new BuildResult(null, null);
        } catch (ReportedException e) {
            return new BuildResult(null, e.getCause());
        } finally {
            stopMeasurement();
            listenerManager.removeListener(listener);
        }
    }

    private BuildActionParameters createBuildActionParameters(StartParameter startParameter) {
        return new DefaultBuildActionParameters(
            System.getProperties(),
            System.getenv(),
            SystemProperties.getInstance().getCurrentDir(),
            startParameter.getLogLevel(),
            false,
            startParameter.isContinuous(),
            interactive,
            ClassPath.EMPTY
        );
    }

    private BuildRequestContext createBuildRequestContext(StandardOutputListener outputListener, StandardOutputListener errorListener) {
        return new DefaultBuildRequestContext(
            new DefaultBuildRequestMetaData(new GradleLauncherMetaData()),
            new DefaultBuildCancellationToken(),
            new NoOpBuildEventConsumer(),
            outputListener, errorListener);
    }

    public void assertCanExecute() {
        assertNull(getExecutable());
        String defaultEncoding = getImplicitJvmSystemProperties().get("file.encoding");
        if (defaultEncoding != null) {
            assertEquals(Charset.forName(defaultEncoding), Charset.defaultCharset());
        }
        Locale defaultLocale = getDefaultLocale();
        if (defaultLocale != null) {
            assertEquals(defaultLocale, Locale.getDefault());
        }
        assertFalse(isRequiresGradleDistribution());
    }

    @Override
    protected TestFile getDefaultTmpDir() {
        // File.createTempFile sets the location of the temp directory to a static variable on the first call.  This prevents future
        // changes to java.io.tmpdir from having any effect in the same process.  We set this to use a common tmp directory for all
        // tests running in the same process so that we don't have a situation where one process initializes with a tmp directory
        // that it then removes, causing an IOException for any future tests that run in the same process and call File.createTempFile.
        return COMMON_TMP;
    }

    private static class BuildListenerImpl implements TaskExecutionGraphListener {
        private final List<String> executedTasks = new CopyOnWriteArrayList<String>();
        private final Set<String> skippedTasks = new CopyOnWriteArraySet<String>();

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
        private final OutputScrapingExecutionResult outputResult;

        public InProcessExecutionResult(List<String> plannedTasks, Set<String> skippedTasks, OutputScrapingExecutionResult outputResult) {
            this.plannedTasks = plannedTasks;
            this.skippedTasks = skippedTasks;
            this.outputResult = outputResult;
        }

        public String getOutput() {
            return outputResult.getOutput();
        }

        @Override
        public String getNormalizedOutput() {
            return outputResult.getNormalizedOutput();
        }

        public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
            outputResult.assertOutputEquals(expectedOutput, ignoreExtraLines, ignoreLineOrder);
            return this;
        }

        @Override
        public ExecutionResult assertOutputContains(String expectedOutput) {
            outputResult.assertOutputContains(expectedOutput);
            return this;
        }

        public String getError() {
            return outputResult.getError();
        }

        public List<String> getExecutedTasks() {
            return new ArrayList<String>(plannedTasks);
        }

        public ExecutionResult assertTasksExecuted(String... taskPaths) {
            List<String> expected = Arrays.asList(taskPaths);
            assertThat(plannedTasks, equalTo(expected));
            outputResult.assertTasksExecuted(taskPaths);
            return this;
        }

        public Set<String> getSkippedTasks() {
            return new HashSet<String>(skippedTasks);
        }

        public ExecutionResult assertTasksSkipped(String... taskPaths) {
            if (GradleContextualExecuter.isParallel()) {
                return this;
            }
            Set<String> expected = new HashSet<String>(Arrays.asList(taskPaths));
            assertThat(skippedTasks, equalTo(expected));
            outputResult.assertTasksSkipped(taskPaths);
            return this;
        }

        public ExecutionResult assertTaskSkipped(String taskPath) {
            if (GradleContextualExecuter.isParallel()) {
                return this;
            }
            assertThat(skippedTasks, hasItem(taskPath));
            outputResult.assertTaskSkipped(taskPath);
            return this;
        }

        public ExecutionResult assertTasksNotSkipped(String... taskPaths) {
            if (GradleContextualExecuter.isParallel()) {
                return this;
            }
            Set<String> expected = new HashSet<String>(Arrays.asList(taskPaths));
            Set<String> notSkipped = getNotSkippedTasks();
            assertThat(notSkipped, equalTo(expected));
            outputResult.assertTasksNotSkipped(taskPaths);
            return this;
        }

        public ExecutionResult assertTaskNotSkipped(String taskPath) {
            if (GradleContextualExecuter.isParallel()) {
                return this;
            }
            assertThat(getNotSkippedTasks(), hasItem(taskPath));
            outputResult.assertTaskNotSkipped(taskPath);
            return this;
        }

        private Set<String> getNotSkippedTasks() {
            Set<String> notSkipped = new HashSet<String>(plannedTasks);
            notSkipped.removeAll(skippedTasks);
            return notSkipped;
        }
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private static final Pattern LOCATION_PATTERN = Pattern.compile("(?m)^((\\w+ )+'.+') line: (\\d+)$");
        private final OutputScrapingExecutionFailure outputFailure;
        private final GradleException failure;
        private final String fileName;
        private final String lineNumber;
        private final String description;

        public InProcessExecutionFailure(List<String> tasks, Set<String> skippedTasks, OutputScrapingExecutionFailure outputFailure,
                                         GradleException failure) {
            super(tasks, skippedTasks, outputFailure);
            this.outputFailure = outputFailure;
            this.failure = failure;

            // Chop up the exception message into its expected parts
            java.util.regex.Matcher matcher = LOCATION_PATTERN.matcher(failure.getMessage());
            if (matcher.find()) {
                fileName = matcher.group(1);
                lineNumber = matcher.group(3);
                description = failure.getMessage().substring(matcher.end()).trim();
            } else {
                fileName = "";
                lineNumber = "";
                description = failure.getMessage().trim();
            }
        }

        public ExecutionFailure assertHasLineNumber(int lineNumber) {
            assertThat(this.lineNumber, equalTo(String.valueOf(lineNumber)));
            outputFailure.assertHasLineNumber(lineNumber);
            return this;
        }

        public ExecutionFailure assertHasFileName(String filename) {
            assertThat(this.fileName, equalTo(filename));
            outputFailure.assertHasFileName(filename);
            return this;
        }

        public ExecutionFailure assertHasResolution(String resolution) {
            outputFailure.assertHasResolution(resolution);
            return this;
        }

        public ExecutionFailure assertHasCause(String description) {
            assertThatCause(startsWith(description));
            return this;
        }

        public ExecutionFailure assertThatCause(Matcher<String> matcher) {
            List<Throwable> causes = new ArrayList<Throwable>();
            extractCauses(failure, causes);
            assertThat(causes, Matchers.<Throwable>hasItem(hasMessage(normalizedLineSeparators(matcher))));
            outputFailure.assertThatCause(matcher);
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
                causes.add(failure);
            }
        }

        public ExecutionFailure assertHasNoCause() {
            if (failure instanceof LocationAwareException) {
                LocationAwareException exception = (LocationAwareException) failure;
                assertThat(exception.getReportableCauses(), isEmpty());
            } else {
                assertThat(failure.getCause(), nullValue());
            }
            outputFailure.assertHasNoCause();
            return this;
        }

        public ExecutionFailure assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
            return this;
        }

        public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
            assertThat(description, normalizedLineSeparators(matcher));
            outputFailure.assertThatDescription(matcher);
            return this;
        }

        public ExecutionFailure assertTestsFailed() {
            new DetailedExecutionFailure(this).assertTestsFailed();
            return this;
        }

        public DependencyResolutionFailure assertResolutionFailure(String configurationPath) {
            return new DependencyResolutionFailure(this, configurationPath);
        }
    }
}
