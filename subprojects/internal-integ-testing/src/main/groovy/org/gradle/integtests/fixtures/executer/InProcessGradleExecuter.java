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

import com.google.common.base.Joiner;
import junit.framework.AssertionFailedError;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskState;
import org.gradle.cli.CommandLineParser;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.integtests.fixtures.FileSystemWatchingHelper;
import org.gradle.integtests.fixtures.logging.GroupedOutputFixture;
import org.gradle.internal.Factory;
import org.gradle.internal.InternalListener;
import org.gradle.internal.IoActions;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.agents.AgentInitializer;
import org.gradle.internal.agents.AgentUtils;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Time;
import org.gradle.launcher.Main;
import org.gradle.launcher.cli.Parameters;
import org.gradle.launcher.cli.ParametersConverter;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;
import org.gradle.tooling.internal.provider.serialization.DeserializeMap;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializeMap;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.IncubationLogger;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.flattenTaskPaths;
import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.normalizeLambdaIds;
import static org.gradle.internal.hash.Hashing.hashString;
import static org.gradle.util.Matchers.normalizedLineSeparators;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class InProcessGradleExecuter extends DaemonGradleExecuter {
    private final ProcessEnvironment processEnvironment = GLOBAL_SERVICES.get(ProcessEnvironment.class);

    public static final TestFile COMMON_TMP = new TestFile(new File("build/tmp"));

    static {
        LoggingManagerInternal loggingManager = GLOBAL_SERVICES.getFactory(LoggingManagerInternal.class).create();
        loggingManager.start();

        GLOBAL_SERVICES.get(AgentInitializer.class).maybeConfigureInstrumentationAgent();
    }

    public InProcessGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    public InProcessGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        super(distribution, testDirectoryProvider, gradleVersion, buildContext);
        waitForChangesToBePickedUpBeforeExecution();
    }

    private void waitForChangesToBePickedUpBeforeExecution() {
        // File system watching is now on by default, so we need to wait for changes to be picked up before each execution.
        beforeExecute(executer -> {
            try {
                FileSystemWatchingHelper.waitForChangesToBePickedUp();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        });
    }

    @Override
    public GradleExecuter reset() {
        DeprecationLogger.reset();
        IncubationLogger.reset();
        return super.reset();
    }

    @Override
    protected ExecutionResult doRun() {
        if (isForkRequired()) {
            return createGradleHandle().waitForFinish();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputStream, errorStream, buildListener);
        if (result.getFailure() != null) {
            throw new UnexpectedBuildFailure(result.getFailure());
        }

        return assertResult(new InProcessExecutionResult(buildListener.executedTasks, buildListener.skippedTasks,
            OutputScrapingExecutionResult.from(outputStream.toString(), errorStream.toString())));
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        if (isForkRequired()) {
            return createGradleHandle().waitForFailure();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        BuildListenerImpl buildListener = new BuildListenerImpl();
        BuildResult result = doRun(outputStream, errorStream, buildListener);
        if (result.getFailure() == null) {
            throw new AssertionError("expected build to fail but it did not.");
        }
        return assertResult(new InProcessExecutionFailure(buildListener.executedTasks, buildListener.skippedTasks,
            OutputScrapingExecutionFailure.from(outputStream.toString(), errorStream.toString()), result.getFailure()));
    }

    private boolean isForkRequired() {
        if (isDaemonExplicitlyRequired() || !getJavaHomeLocation().equals(Jvm.current().getJavaHome())) {
            return true;
        }
        File gradleProperties = new File(getWorkingDir(), "gradle.properties");
        if (gradleProperties.isFile()) {
            Properties properties = GUtil.loadProperties(gradleProperties);
            return properties.getProperty("org.gradle.java.home") != null || properties.getProperty("org.gradle.jvmargs") != null;
        }
        boolean isInstrumentationEnabledForProcess = isAgentInstrumentationEnabled();
        boolean differentInstrumentationRequested = getAllArgs().stream().anyMatch(
            ("-D" + DaemonBuildOptions.ApplyInstrumentationAgentOption.GRADLE_PROPERTY + "=" + !isInstrumentationEnabledForProcess)::equals);
        return differentInstrumentationRequested;
    }

    private <T extends ExecutionResult> T assertResult(T result) {
        getResultAssertion().execute(result);
        return result;
    }

    @Override
    protected GradleHandle createGradleHandle() {
        configureConsoleCommandLineArgs();
        return super.createGradleHandle();
    }

    @Override
    protected Factory<JavaExecHandleBuilder> getExecHandleFactory() {
        return () -> {
            NativeServicesTestFixture.initialize();
            GradleInvocation invocation = buildInvocation();
            JavaExecHandleBuilder builder = TestFiles.execFactory().newJavaExec();
            builder.workingDir(getWorkingDir());
            builder.setExecutable(new File(getJavaHomeLocation(), "bin/java"));
            builder.classpath(getExecHandleFactoryClasspath());
            builder.jvmArgs(invocation.launcherJvmArgs);
            // Apply the agent to the newly created daemon. The feature flag decides if it is going to be used.
            for (File agent : cleanup(GLOBAL_SERVICES.get(ModuleRegistry.class).getModule(AgentUtils.AGENT_MODULE_NAME).getClasspath().getAsFiles())) {
                builder.jvmArgs("-javaagent:" + agent.getAbsolutePath());
            }
            builder.environment(invocation.environmentVars);

            builder.getMainClass().set(Main.class.getName());
            builder.args(invocation.args);
            builder.setStandardInput(connectStdIn());

            return builder;
        };
    }

    private Collection<File> getExecHandleFactoryClasspath() {
        Collection<File> classpath = cleanup(GLOBAL_SERVICES.get(ModuleRegistry.class).getAdditionalClassPath().getAsFiles());
        if (!OperatingSystem.current().isWindows()) {
            return classpath;
        }
        // Use a Class-Path manifest JAR to circumvent too long command line issues on Windows (cap 8191)
        // Classpath is huge here because it's the test runtime classpath
        return Collections.singleton(getClasspathManifestJarFor(classpath));
    }

    private Collection<File> cleanup(List<File> files) {
        List<File> result = new LinkedList<>();
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

    private File getClasspathManifestJarFor(Collection<File> classpath) {
        String cpString = classpath.stream()
            .map(File::toURI)
            .map(Object::toString)
            .collect(Collectors.joining(" "));
        File cpJar = new File(getDefaultTmpDir(), "daemon-classpath-manifest-" + hashString(cpString).toCompactString() + ".jar");
        if (!cpJar.isFile()) {
            // Make sure the parent exists or the jar creation might fail
            cpJar.getParentFile().mkdirs();
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, cpString);
            JarOutputStream output = null;
            try {
                output = new JarOutputStream(new FileOutputStream(cpJar), manifest);
                output.putNextEntry(new JarEntry("META-INF/"));
                output.closeEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                IoActions.closeQuietly(output);
            }
        }
        return cpJar;
    }

    private BuildResult doRun(OutputStream outputStream, OutputStream errorStream, BuildListenerImpl listener) {
        // Capture the current state of things that we will change during execution
        InputStream originalStdIn = System.in;
        Properties originalSysProperties = new Properties();
        originalSysProperties.putAll(System.getProperties());
        File originalUserDir = new File(originalSysProperties.getProperty("user.dir")).getAbsoluteFile();
        Map<String, String> originalEnv = new HashMap<>(System.getenv());

        GradleInvocation invocation = buildInvocation();
        Set<String> changedEnvVars = new HashSet<>(invocation.environmentVars.keySet());

        try {
            return executeBuild(invocation, outputStream, errorStream, listener);
        } finally {
            // Restore the environment
            System.setProperties(originalSysProperties);
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

    private LoggingManagerInternal createLoggingManager(StartParameter startParameter, OutputStream outputStream, OutputStream errorStream) {
        LoggingManagerInternal loggingManager = GLOBAL_SERVICES.getFactory(LoggingManagerInternal.class).create();
        loggingManager.captureSystemSources();

        ConsoleOutput consoleOutput = startParameter.getConsoleOutput();
        loggingManager.attachConsole(new TeeOutputStream(System.out, outputStream), new TeeOutputStream(System.err, errorStream), consoleOutput, consoleAttachment.getConsoleMetaData());

        return loggingManager;
    }

    private BuildResult executeBuild(GradleInvocation invocation, OutputStream outputStream, OutputStream errorStream, BuildListenerImpl listener) {
        // Augment the environment for the execution
        System.setIn(connectStdIn());
        processEnvironment.maybeSetProcessDir(getWorkingDir());
        for (Map.Entry<String, String> entry : invocation.environmentVars.entrySet()) {
            processEnvironment.maybeSetEnvironmentVariable(entry.getKey(), entry.getValue());
        }
        Map<String, String> implicitJvmSystemProperties = getImplicitJvmSystemProperties();
        System.getProperties().putAll(implicitJvmSystemProperties);

        // TODO: Reuse more of CommandlineActionFactory
        CommandLineParser parser = new CommandLineParser();
        FileCollectionFactory fileCollectionFactory = TestFiles.fileCollectionFactory();
        ParametersConverter parametersConverter = new ParametersConverter(new BuildLayoutFactory(), fileCollectionFactory);
        parametersConverter.configure(parser);
        Parameters parameters = parametersConverter.convert(parser.parse(getAllArgs()), getWorkingDir());

        BuildActionExecuter<BuildActionParameters, BuildRequestContext> actionExecuter = GLOBAL_SERVICES.get(BuildActionExecuter.class);

        ListenerManager listenerManager = GLOBAL_SERVICES.get(ListenerManager.class);
        listenerManager.addListener(listener);

        try {
            // TODO: Reuse more of BuildActionsFactory
            StartParameterInternal startParameter = parameters.getStartParameter();
            BuildAction action = new ExecuteBuildAction(startParameter);
            BuildActionParameters buildActionParameters = createBuildActionParameters(startParameter);
            BuildRequestContext buildRequestContext = createBuildRequestContext();

            LoggingManagerInternal loggingManager = createLoggingManager(startParameter, outputStream, errorStream);
            loggingManager.start();

            try {
                startMeasurement();
                try {
                    BuildActionResult result = actionExecuter.execute(action, buildActionParameters, buildRequestContext);
                    if (result.getException() != null) {
                        return new BuildResult(null, result.getException());
                    }
                    if (result.getFailure() != null) {
                        PayloadSerializer payloadSerializer = new PayloadSerializer(new TestClassLoaderRegistry());
                        return new BuildResult(null, (RuntimeException) payloadSerializer.deserialize(result.getFailure()));
                    }
                    return new BuildResult(null, null);
                } finally {
                    stopMeasurement();
                }
            } finally {
                loggingManager.stop();
            }
        } finally {
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
            ClassPath.EMPTY
        );
    }

    private BuildRequestContext createBuildRequestContext() {
        return new DefaultBuildRequestContext(
            new DefaultBuildRequestMetaData(new GradleLauncherMetaData(), Time.currentTimeMillis(), interactive),
            new DefaultBuildCancellationToken(),
            new NoOpBuildEventConsumer());
    }

    @Override
    public void assertCanExecute() {
        assertNull(getExecutable());
    }

    @Override
    protected TestFile getDefaultTmpDir() {
        // File.createTempFile sets the location of the temp directory to a static variable on the first call.  This prevents future
        // changes to java.io.tmpdir from having any effect in the same process.  We set this to use a common tmp directory for all
        // tests running in the same process so that we don't have a situation where one process initializes with a tmp directory
        // that it then removes, causing an IOException for any future tests that run in the same process and call File.createTempFile.
        return COMMON_TMP;
    }

    @Override
    public GradleExecuter withTestConsoleAttached() {
        return withTestConsoleAttached(ConsoleAttachment.ATTACHED);
    }

    @Override
    public GradleExecuter withTestConsoleAttached(ConsoleAttachment consoleAttachment) {
        this.consoleAttachment = consoleAttachment;
        return this;
    }

    private static class BuildListenerImpl implements TaskExecutionListener, InternalListener {
        private final List<String> executedTasks = new CopyOnWriteArrayList<>();
        private final Set<String> skippedTasks = new CopyOnWriteArraySet<>();

        @Override
        public void beforeExecute(Task task) {
            String taskPath = path(task);
            executedTasks.add(taskPath);
        }

        @Override
        public void afterExecute(Task task, TaskState state) {
            String taskPath = path(task);
            if (state.getSkipped()) {
                skippedTasks.add(taskPath);
            }
        }

        private String path(Task task) {
            return ((TaskInternal) task).getIdentityPath().getPath();
        }
    }

    public static class InProcessExecutionResult implements ExecutionResult {
        protected static final Spec<String> NOT_BUILD_SRC_TASK = t -> !t.startsWith(":buildSrc:");
        protected final List<String> executedTasks;
        protected final Set<String> skippedTasks;
        private final ExecutionResult outputResult;

        InProcessExecutionResult(List<String> executedTasks, Set<String> skippedTasks, ExecutionResult outputResult) {
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
            this.outputResult = outputResult;
        }

        @Override
        public ExecutionResult getIgnoreBuildSrc() {
            List<String> executedTasks = CollectionUtils.filter(this.executedTasks, NOT_BUILD_SRC_TASK);
            Set<String> skippedTasks = CollectionUtils.filter(this.skippedTasks, NOT_BUILD_SRC_TASK);
            return new InProcessExecutionResult(executedTasks, skippedTasks, outputResult.getIgnoreBuildSrc());
        }

        @Override
        public String getOutput() {
            return outputResult.getOutput();
        }

        @Override
        public String getNormalizedOutput() {
            return outputResult.getNormalizedOutput();
        }

        @Override
        public String getFormattedOutput() {
            return outputResult.getFormattedOutput();
        }

        @Override
        public String getPlainTextOutput() {
            return outputResult.getPlainTextOutput();
        }

        @Override
        public GroupedOutputFixture getGroupedOutput() {
            return outputResult.getGroupedOutput();
        }

        @Override
        public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
            outputResult.assertOutputEquals(expectedOutput, ignoreExtraLines, ignoreLineOrder);
            return this;
        }

        @Override
        public ExecutionResult assertNotOutput(String expectedOutput) {
            outputResult.assertNotOutput(expectedOutput);
            return this;
        }

        @Override
        public ExecutionResult assertOutputContains(String expectedOutput) {
            outputResult.assertOutputContains(expectedOutput);
            return this;
        }

        @Override
        public ExecutionResult assertContentContains(String content, String expectedOutput, String label) {
            outputResult.assertContentContains(content, expectedOutput, label);
            return null;
        }

        @Override
        public ExecutionResult assertHasPostBuildOutput(String expectedOutput) {
            outputResult.assertHasPostBuildOutput(expectedOutput);
            return this;
        }

        @Override
        public ExecutionResult assertNotPostBuildOutput(String expectedOutput) {
            outputResult.assertNotPostBuildOutput(expectedOutput);
            return this;
        }

        @Override
        public boolean hasErrorOutput(String expectedOutput) {
            return outputResult.hasErrorOutput(expectedOutput);
        }

        @Override
        public ExecutionResult assertHasErrorOutput(String expectedOutput) {
            outputResult.assertHasErrorOutput(expectedOutput);
            return this;
        }

        @Override
        public String getError() {
            return outputResult.getError();
        }

        @Override
        public String getOutputLineThatContains(String text) {
            return outputResult.getOutputLineThatContains(text);
        }

        @Override
        public String getPostBuildOutputLineThatContains(String text) {
            return outputResult.getPostBuildOutputLineThatContains(text);
        }

        @Override
        public ExecutionResult assertTasksExecutedInOrder(Object... taskPaths) {
            Set<String> expected = TaskOrderSpecs.exact(taskPaths).getTasks();
            assertTasksExecuted(expected);
            assertTaskOrder(taskPaths);
            outputResult.assertTasksExecutedInOrder(taskPaths);
            return this;
        }

        @Override
        public ExecutionResult assertTasksExecuted(Object... taskPaths) {
            Set<String> flattenedTasks = new TreeSet<>(flattenTaskPaths(taskPaths));
            assertEquals(new TreeSet<>(flattenedTasks), new TreeSet<>(executedTasks));
            outputResult.assertTasksExecuted(flattenedTasks);
            return this;
        }

        @Override
        public ExecutionResult assertTasksExecutedAndNotSkipped(Object... taskPaths) {
            assertTasksExecuted(taskPaths);
            assertTasksNotSkipped(taskPaths);
            return this;
        }

        @Override
        public ExecutionResult assertTaskExecuted(String taskPath) {
            assertThat(executedTasks, hasItem(taskPath));
            outputResult.assertTaskExecuted(taskPath);
            return this;
        }

        @Override
        public ExecutionResult assertTaskNotExecuted(String taskPath) {
            assertThat(executedTasks, not(hasItem(taskPath)));
            outputResult.assertTaskNotExecuted(taskPath);
            return this;
        }

        @Override
        public ExecutionResult assertTaskOrder(Object... taskPaths) {
            TaskOrderSpecs.exact(taskPaths).assertMatches(-1, executedTasks);
            outputResult.assertTaskOrder(taskPaths);
            return this;
        }

        @Override
        public ExecutionResult assertTasksSkipped(Object... taskPaths) {
            Set<String> expected = new TreeSet<>(flattenTaskPaths(taskPaths));
            assertThat(skippedTasks, equalTo(expected));
            outputResult.assertTasksSkipped(expected);
            return this;
        }

        @Override
        public ExecutionResult assertTaskSkipped(String taskPath) {
            assertThat(skippedTasks, hasItem(taskPath));
            outputResult.assertTaskSkipped(taskPath);
            return this;
        }

        @Override
        public ExecutionResult assertTasksNotSkipped(Object... taskPaths) {
            Set<String> expected = new TreeSet<>(flattenTaskPaths(taskPaths));
            Set<String> notSkipped = getNotSkippedTasks();
            assertThat(notSkipped, equalTo(expected));
            outputResult.assertTasksNotSkipped(expected);
            return this;
        }

        @Override
        public ExecutionResult assertTaskNotSkipped(String taskPath) {
            assertThat(getNotSkippedTasks(), hasItem(taskPath));
            outputResult.assertTaskNotSkipped(taskPath);
            return this;
        }

        private Set<String> getNotSkippedTasks() {
            Set<String> notSkipped = new TreeSet<>(executedTasks);
            notSkipped.removeAll(skippedTasks);
            return notSkipped;
        }

        @Override
        public void assertResultVisited() {
            outputResult.assertResultVisited();
        }
    }

    private static class InProcessExecutionFailure extends InProcessExecutionResult implements ExecutionFailure {
        private static final Pattern LOCATION_PATTERN = Pattern.compile("(?m)^((\\w+ )+'.+') line: (\\d+)$");
        private final ExecutionFailure outputFailure;
        private final Throwable failure;
        private final List<String> fileNames = new ArrayList<>();
        private final List<String> lineNumbers = new ArrayList<>();
        private final List<FailureDetails> failures = new ArrayList<>();

        InProcessExecutionFailure(List<String> tasks, Set<String> skippedTasks, ExecutionFailure outputFailure, Throwable failure) {
            super(tasks, skippedTasks, outputFailure);
            this.outputFailure = outputFailure;
            this.failure = failure;

            if (failure instanceof MultipleBuildFailures) {
                for (Throwable cause : ((MultipleBuildFailures) failure).getCauses()) {
                    extractDetails(cause);
                }
            } else {
                extractDetails(failure);
            }
        }

        private void extractDetails(Throwable failure) {
            List<String> causes = new ArrayList<>();
            extractCauses(failure, causes);

            String failureMessage = failure.getMessage() == null ? "" : normalizeLambdaIds(failure.getMessage());
            java.util.regex.Matcher matcher = LOCATION_PATTERN.matcher(failureMessage);
            if (matcher.find()) {
                fileNames.add(matcher.group(1));
                lineNumbers.add(matcher.group(3));
                failures.add(new FailureDetails(failure, failureMessage.substring(matcher.end()).trim(), causes));
            } else {
                failures.add(new FailureDetails(failure, failureMessage.trim(), causes));
            }
        }

        @Override
        public InProcessExecutionFailure getIgnoreBuildSrc() {
            List<String> executedTasks = CollectionUtils.filter(this.executedTasks, NOT_BUILD_SRC_TASK);
            Set<String> skippedTasks = CollectionUtils.filter(this.skippedTasks, NOT_BUILD_SRC_TASK);
            return new InProcessExecutionFailure(executedTasks, skippedTasks, outputFailure.getIgnoreBuildSrc(), failure);
        }

        @Override
        public ExecutionFailure assertHasLineNumber(int lineNumber) {
            outputFailure.assertHasLineNumber(lineNumber);
            assertThat(this.lineNumbers, hasItem(equalTo(String.valueOf(lineNumber))));
            return this;
        }

        @Override
        public ExecutionFailure assertHasFileName(String filename) {
            outputFailure.assertHasFileName(filename);
            assertThat(this.fileNames, hasItem(equalTo(filename)));
            return this;
        }

        @Override
        public ExecutionFailure assertHasResolutions(String... resolutions) {
            outputFailure.assertHasResolutions(resolutions);
            return this;
        }

        @Override
        public ExecutionFailure assertHasResolution(String resolution) {
            outputFailure.assertHasResolution(resolution);
            return this;
        }

        @Override
        public ExecutionFailure assertHasFailures(int count) {
            outputFailure.assertHasFailures(count);
            if (failures.size() != count) {
                throw new AssertionFailedError(String.format("Expected %s failures, but found %s", count, failures.size()));
            }
            return this;
        }

        @Override
        public ExecutionFailure assertHasCause(String description) {
            assertThatCause(startsWith(description));
            return this;
        }

        @Override
        public ExecutionFailure assertThatCause(Matcher<? super String> matcher) {
            outputFailure.assertThatCause(matcher);
            Set<String> seen = new LinkedHashSet<>();
            Matcher<String> messageMatcher = normalizedLineSeparators(matcher);
            for (FailureDetails failure : failures) {
                for (String cause : failure.causes) {
                    if (messageMatcher.matches(cause)) {
                        return this;
                    }
                    seen.add(cause);
                }
            }
            fail(String.format("Could not find matching cause in: %s%nFailure is: %s", seen, failure));
            return this;
        }

        private void extractCauses(Throwable failure, List<String> causes) {
            if (failure instanceof MultipleBuildFailures) {
                MultipleBuildFailures exception = (MultipleBuildFailures) failure;
                for (Throwable componentFailure : exception.getCauses()) {
                    extractCauses(componentFailure, causes);
                }
            } else if (failure instanceof LocationAwareException) {
                for (Throwable cause : ((LocationAwareException) failure).getReportableCauses()) {
                    causes.add(cause.getMessage());
                }
            } else {
                causes.add(failure.getMessage());
            }
        }

        @Override
        public ExecutionFailure assertHasNoCause(String description) {
            outputFailure.assertHasNoCause(description);
            Matcher<String> matcher = containsString(description);
            for (FailureDetails failure : failures) {
                for (String cause : failure.causes) {
                    if (matcher.matches(cause)) {
                        throw new AssertionFailedError(String.format("Expected no failure with description '%s', found: %s", description, cause));
                    }
                }
            }
            return this;
        }

        @Override
        public ExecutionFailure assertHasNoCause() {
            outputFailure.assertHasNoCause();
            for (FailureDetails failure : failures) {
                assertEquals(0, failure.causes.size());
            }
            return this;
        }

        @Override
        public ExecutionFailure assertHasDescription(String context) {
            assertThatDescription(startsWith(context));
            return this;
        }

        @Override
        public ExecutionFailure assertThatDescription(Matcher<? super String> matcher) {
            outputFailure.assertThatDescription(matcher);
            assertHasFailure(matcher, f -> {
            });
            return this;
        }

        @Override
        public ExecutionFailure assertHasFailure(String description, Consumer<? super Failure> action) {
            outputFailure.assertHasFailure(description, action);
            assertHasFailure(startsWith(description), action);
            return this;
        }

        private void assertHasFailure(Matcher<? super String> matcher, Consumer<? super Failure> action) {
            Matcher<String> normalized = normalizedLineSeparators(matcher);
            for (FailureDetails failure : failures) {
                if (normalized.matches(failure.description)) {
                    action.accept(failure);
                    return;
                }
            }
            StringDescription description = new StringDescription();
            matcher.describeTo(description);
            throw new AssertionFailedError(String.format("Could not find any failure with description %s, failures:%s\n", description, Joiner.on("\n").join(failures)));
        }

        @Override
        public ExecutionFailure assertTestsFailed() {
            new DetailedExecutionFailure(this).assertTestsFailed();
            return this;
        }

        @Override
        public DependencyResolutionFailure assertResolutionFailure(String configurationPath) {
            return new DependencyResolutionFailure(this, configurationPath);
        }
    }

    private static class FailureDetails extends AbstractFailure {
        final Throwable failure;

        public FailureDetails(Throwable failure, String description, List<String> causes) {
            super(description, causes);
            this.failure = failure;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private static class TestClassLoaderRegistry implements PayloadClassLoaderRegistry {
        @Override
        public SerializeMap newSerializeSession() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DeserializeMap newDeserializeSession() {
            return (classLoaderDetails, className) -> {
                // Assume everything is loaded into the current classloader
                return Class.forName(className);
            };
        }
    }
}
