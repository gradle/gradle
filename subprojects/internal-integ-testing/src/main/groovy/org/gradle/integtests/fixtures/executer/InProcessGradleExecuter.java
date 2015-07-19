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
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.cli.CommandLineParser;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.*;
import org.gradle.internal.Factory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.launcher.Main;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.cli.Parameters;
import org.gradle.launcher.cli.ParametersConverter;
import org.gradle.launcher.daemon.configuration.DaemonUsage;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.ShowStacktrace;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.DeprecationLogger;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

class InProcessGradleExecuter extends AbstractGradleExecuter {
    private static final ServiceRegistry GLOBAL_SERVICES = ServiceRegistryBuilder.builder()
        .displayName("Global services")
        .parent(LoggingServiceRegistry.newCommandLineProcessLogging())
        .parent(NativeServicesTestFixture.getInstance())
        .provider(new GlobalScopeServices(true))
        .build();
    private final ProcessEnvironment processEnvironment = GLOBAL_SERVICES.get(ProcessEnvironment.class);

    public static final TestFile COMMON_TMP = new TestFile(new File("build/tmp"));

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
        if (isRequireDaemon()) {
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
        if (isRequireDaemon()) {
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

    private <T extends ExecutionResult> T assertResult(T result) {
        getResultAssertion().execute(result);
        return result;
    }

    @Override
    protected GradleHandle doStart() {
        return new ForkingGradleHandle(getResultAssertion(), getDefaultCharacterEncoding(), new Factory<JavaExecHandleBuilder>() {
            public JavaExecHandleBuilder create() {
                JavaExecHandleBuilder builder = new JavaExecHandleBuilder(TestFiles.resolver());
                builder.workingDir(getWorkingDir());
                Collection<File> classpath = GLOBAL_SERVICES.get(ModuleRegistry.class).getAdditionalClassPath().getAsFiles();
                builder.classpath(classpath);
                builder.jvmArgs(getForkingOpts());
                builder.setMain(Main.class.getName());
                builder.args(getAllArgs());
                builder.setStandardInput(getStdin());
                if (isDebug()) {
                    builder.jvmArgs(DEBUG_ARGS);
                }
                return builder;
            }
        }).start();
    }

    private List<String> getForkingOpts() {
        if (isRequireDaemon()) {
            List<String> args = new ArrayList<String>();
            // Don't use the default daemon JVM args, force to something sane
            args.add("-Dorg.gradle.jvmargs=-ea");
            args.addAll(getGradleOpts());
            return args;
        } else {
            return getGradleOpts();
        }
    }

    private BuildResult doRun(StandardOutputListener outputListener, StandardOutputListener errorListener, BuildListenerImpl listener) {
        // Capture the current state of things that we will change during execution
        InputStream originalStdIn = System.in;
        Properties originalSysProperties = new Properties();
        originalSysProperties.putAll(System.getProperties());
        File originalUserDir = new File(originalSysProperties.getProperty("user.dir"));
        Map<String, String> originalEnv = new HashMap<String, String>(System.getenv());

        // Augment the environment for the execution
        System.setIn(getStdin());
        processEnvironment.maybeSetProcessDir(getWorkingDir());
        for (Map.Entry<String, String> entry : getEnvironmentVars().entrySet()) {
            processEnvironment.maybeSetEnvironmentVariable(entry.getKey(), entry.getValue());
        }
        Map<String, String> implicitJvmSystemProperties = getImplicitJvmSystemProperties();
        System.getProperties().putAll(implicitJvmSystemProperties);

        // TODO: Fix tests that rely on this being set before we process arguments like this...
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(getWorkingDir());
        startParameter.setShowStacktrace(ShowStacktrace.ALWAYS);

        CommandLineParser parser = new CommandLineParser();
        ParametersConverter parametersConverter = new ParametersConverter();
        parametersConverter.configure(parser);
        parametersConverter.convert(parser.parse(getAllArgs()), new Parameters(startParameter));

        BuildActionExecuter<BuildActionParameters> actionExecuter = GLOBAL_SERVICES.get(BuildActionExecuter.class);

        ListenerManager listenerManager = GLOBAL_SERVICES.get(ListenerManager.class);
        listenerManager.addListener(listener);

        try {
            // TODO: Reuse more of BuildActionsFactory
            BuildAction action = new ExecuteBuildAction(startParameter);
            BuildActionParameters buildActionParameters = new DefaultBuildActionParameters(
                System.getProperties(),
                System.getenv(),
                SystemProperties.getInstance().getCurrentDir(),
                startParameter.getLogLevel(),
                DaemonUsage.EXPLICITLY_DISABLED,
                startParameter.isContinuous(),
                interactive
            );
            BuildRequestContext buildRequestContext = new DefaultBuildRequestContext(
                new DefaultBuildRequestMetaData(new GradleLauncherMetaData(),
                    ManagementFactory.getRuntimeMXBean().getStartTime()),
                new DefaultBuildCancellationToken(),
                new NoOpBuildEventConsumer(),
                outputListener, errorListener);
            actionExecuter.execute(action, buildRequestContext, buildActionParameters, GLOBAL_SERVICES);
            return new BuildResult(null, null);
        } catch (ReportedException e) {
            return new BuildResult(null, e.getCause());
        } finally {
            // Restore the environment
            System.setProperties(originalSysProperties);
            processEnvironment.maybeSetProcessDir(originalUserDir);
            for (String envVar : getEnvironmentVars().keySet()) {
                String oldValue = originalEnv.get(envVar);
                if (oldValue != null) {
                    processEnvironment.maybeSetEnvironmentVariable(envVar, oldValue);
                } else {
                    processEnvironment.maybeRemoveEnvironmentVariable(envVar);
                }
            }
            listenerManager.removeListener(listener);
            System.setIn(originalStdIn);
        }
    }

    public void assertCanExecute() {
        assertNull(getExecutable());
        assertEquals(getJavaHome(), Jvm.current().getJavaHome());
        String defaultEncoding = getImplicitJvmSystemProperties().get("file.encoding");
        if (defaultEncoding != null) {
            assertEquals(Charset.forName(defaultEncoding), Charset.defaultCharset());
        }
        Locale defaultLocale = getDefaultLocale();
        if (defaultLocale != null) {
            assertEquals(defaultLocale, Locale.getDefault());
        }
        assertFalse(isRequireGradleHome());
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
            assertThatCause(normalizedLineSeparators(startsWith(description)));
            return this;
        }

        public ExecutionFailure assertThatCause(final Matcher<String> matcher) {
            List<Throwable> causes = new ArrayList<Throwable>();
            extractCauses(failure, causes);
            assertThat(causes, Matchers.<Throwable>hasItem(hasMessage(matcher)));
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
