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

import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.JavaVersion;
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
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.integtests.fixtures.FileSystemWatchingHelper;
import org.gradle.internal.Factory;
import org.gradle.internal.InternalListener;
import org.gradle.internal.IoActions;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instrumentation.agent.AgentInitializer;
import org.gradle.internal.instrumentation.agent.AgentUtils;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Time;
import org.gradle.launcher.cli.BuildEnvironmentConfigurationConverter;
import org.gradle.launcher.cli.Parameters;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.exec.BuildActionExecutor;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.flattenTaskPaths;
import static org.gradle.internal.hash.Hashing.hashString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Runs Gradle within the current process.
 * <p>
 * There is some initialization happening in {@link InProcessGradleExecutorInitialization}, so that the global services
 * are correctly in place.
 */
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

        return assertResult(
            new InProcessExecutionResult(
                OutputScrapingExecutionResult.from(outputStream.toString(), errorStream.toString()),
                buildListener.executedTasks,
                buildListener.skippedTasks
            )
        );
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

        return assertResult(
            new ExecutionFailureWithThrowable(
                new InProcessExecutionFailure(
                    buildListener.executedTasks,
                    buildListener.skippedTasks,
                    OutputScrapingExecutionFailure.from(outputStream.toString(), errorStream.toString())
                ),
                result.getFailure()
            )
        );
    }

    private boolean isForkRequired() {
        if (isDaemonExplicitlyRequired() || !getJavaHomeLocation().equals(Jvm.current().getJavaHome())) {
            return true;
        }
        File daemonJvmProperties = new File(getWorkingDir(), "gradle/gradle-daemon-jvm.properties");
        if (daemonJvmProperties.isFile()) {
            Properties properties = GUtil.loadProperties(daemonJvmProperties);
            String requestedVersion = properties.getProperty("toolchainVersion");
            if (requestedVersion != null) {
                try {
                    JavaVersion requestedJavaVersion = JavaVersion.toVersion(requestedVersion);
                    return !requestedJavaVersion.equals(JavaVersion.current());
                } catch (Exception e) {
                    // The build properties may be intentionally invalid, so we should attempt to test this outside the
                    // in-process executor
                    return true;
                }
            }
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

            builder.getMainClass().set("org.gradle.launcher.Main");
            builder.args(invocation.args);
            builder.getStandardInput().set(connectStdIn());

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
        BuildEnvironmentConfigurationConverter buildEnvironmentConfigurationConverter = new BuildEnvironmentConfigurationConverter(new BuildLayoutFactory(), fileCollectionFactory);
        buildEnvironmentConfigurationConverter.configure(parser);
        Parameters parameters = buildEnvironmentConfigurationConverter.convertParameters(parser.parse(getAllArgs()), getWorkingDir());

        BuildActionExecutor<BuildActionParameters, BuildRequestContext> actionExecuter = GLOBAL_SERVICES.get(BuildActionExecutor.class);

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
            new DefaultBuildRequestMetaData(Time.currentTimeMillis(), interactive),
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

    private static class InProcessExecutionResult implements DelegatingExecutionResult {
        protected static final Spec<String> NOT_BUILD_SRC_TASK = t -> !t.startsWith(":buildSrc:");

        private final ExecutionResult delegate;
        protected final List<String> executedTasks;
        protected final Set<String> skippedTasks;


        public InProcessExecutionResult(ExecutionResult delegate, List<String> executedTasks, Set<String> skippedTasks) {
            this.delegate = delegate;
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
        }

        @Override
        public ExecutionResult getDelegate() {
            return delegate;
        }

        @Override
        public ExecutionResult getIgnoreBuildSrc() {
            List<String> executedTasks = CollectionUtils.filter(this.executedTasks, NOT_BUILD_SRC_TASK);
            Set<String> skippedTasks = CollectionUtils.filter(this.skippedTasks, NOT_BUILD_SRC_TASK);
            return new InProcessExecutionResult(delegate.getIgnoreBuildSrc(), executedTasks, skippedTasks);
        }

        @Override
        public ExecutionResult assertTasksExecutedInOrder(Object... taskPaths) {
            Set<String> expected = TaskOrderSpecs.exact(taskPaths).getTasks();
            assertTasksExecuted(expected);
            assertTaskOrder(taskPaths);
            delegate.assertTasksExecutedInOrder(taskPaths);
            return this;
        }

        @Override
        public ExecutionResult assertTasksExecuted(Object... taskPaths) {
            Set<String> flattenedTasks = new TreeSet<>(flattenTaskPaths(taskPaths));
            assertEquals(new TreeSet<>(flattenedTasks), new TreeSet<>(executedTasks));
            delegate.assertTasksExecuted(flattenedTasks);
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
            delegate.assertTaskExecuted(taskPath);
            return this;
        }

        @Override
        public ExecutionResult assertTaskNotExecuted(String taskPath) {
            assertThat(executedTasks, not(hasItem(taskPath)));
            delegate.assertTaskNotExecuted(taskPath);
            return this;
        }

        @Override
        public ExecutionResult assertTaskOrder(Object... taskPaths) {
            TaskOrderSpecs.exact(taskPaths).assertMatches(-1, executedTasks);
            delegate.assertTaskOrder(taskPaths);
            return this;
        }

        @Override
        public ExecutionResult assertTasksSkipped(Object... taskPaths) {
            Set<String> expected = new TreeSet<>(flattenTaskPaths(taskPaths));
            assertThat(skippedTasks, equalTo(expected));
            delegate.assertTasksSkipped(expected);
            return this;
        }

        @Override
        public ExecutionResult assertTaskSkipped(String taskPath) {
            assertThat(skippedTasks, hasItem(taskPath));
            delegate.assertTaskSkipped(taskPath);
            return this;
        }

        @Override
        public ExecutionResult assertTasksNotSkipped(Object... taskPaths) {
            Set<String> expected = new TreeSet<>(flattenTaskPaths(taskPaths));
            Set<String> notSkipped = getNotSkippedTasks();
            assertThat(notSkipped, equalTo(expected));
            delegate.assertTasksNotSkipped(expected);
            return this;
        }

        @Override
        public ExecutionResult assertTaskNotSkipped(String taskPath) {
            assertThat(getNotSkippedTasks(), hasItem(taskPath));
            delegate.assertTaskNotSkipped(taskPath);
            return this;
        }

        private Set<String> getNotSkippedTasks() {
            Set<String> notSkipped = new TreeSet<>(executedTasks);
            notSkipped.removeAll(skippedTasks);
            return notSkipped;
        }
    }

    public static class InProcessExecutionFailure extends InProcessExecutionResult implements DelegatingExecutionFailure {

        private final ExecutionFailure delegate;

        public InProcessExecutionFailure(List<String> tasks, Set<String> skippedTasks, ExecutionFailure delegate) {
            super(delegate, tasks, skippedTasks);
            this.delegate = delegate;
        }

        @Override
        public ExecutionFailure getDelegate() {
            return delegate;
        }

        @Override
        public InProcessExecutionFailure getIgnoreBuildSrc() {
            List<String> executedTasks = CollectionUtils.filter(this.executedTasks, NOT_BUILD_SRC_TASK);
            Set<String> skippedTasks = CollectionUtils.filter(this.skippedTasks, NOT_BUILD_SRC_TASK);
            return new InProcessExecutionFailure(executedTasks, skippedTasks, delegate.getIgnoreBuildSrc());
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
