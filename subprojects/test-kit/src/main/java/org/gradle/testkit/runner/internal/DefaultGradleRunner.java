/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal;

import org.apache.commons.io.output.WriterOutputStream;
import org.gradle.api.Action;
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.InvalidRunnerConfigurationException;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.gradle.testkit.runner.UnexpectedBuildSuccess;
import org.gradle.testkit.runner.internal.io.SynchronizedOutputStream;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultGradleRunner extends GradleRunner {

    public static final String TEST_KIT_DIR_SYS_PROP = "org.gradle.testkit.dir";
    public static final String DEBUG_SYS_PROP = "org.gradle.testkit.debug";

    private final GradleExecutor gradleExecutor;

    private GradleProvider gradleProvider;
    private TestKitDirProvider testKitDirProvider;
    private File projectDirectory;
    private List<String> arguments = Collections.emptyList();
    private List<String> jvmArguments = Collections.emptyList();
    private ClassPath classpath = ClassPath.EMPTY;
    private boolean debug;
    private OutputStream standardOutput;
    private OutputStream standardError;
    private InputStream standardInput;
    private boolean forwardingSystemStreams;
    private Map<String, String> environment;

    public DefaultGradleRunner() {
        this(new ToolingApiGradleExecutor(), calculateTestKitDirProvider(SystemProperties.getInstance()));
    }

    DefaultGradleRunner(GradleExecutor gradleExecutor, TestKitDirProvider testKitDirProvider) {
        this.gradleExecutor = gradleExecutor;
        this.testKitDirProvider = testKitDirProvider;
        this.debug = Boolean.getBoolean(DEBUG_SYS_PROP);
    }

    private static TestKitDirProvider calculateTestKitDirProvider(SystemProperties systemProperties) {
        return systemProperties.withSystemProperties((Factory<TestKitDirProvider>) () -> {
            if (System.getProperties().containsKey(TEST_KIT_DIR_SYS_PROP)) {
                return new ConstantTestKitDirProvider(new File(System.getProperty(TEST_KIT_DIR_SYS_PROP)));
            } else {
                TemporaryFileProvider temporaryFileProvider = new DefaultTemporaryFileProvider(() -> {
                    String rootTmpDir = SystemProperties.getInstance().getWorkerTmpDir();
                    if (rootTmpDir == null) {
                        @SuppressWarnings("deprecation")
                        String javaIoTmpDir = SystemProperties.getInstance().getJavaIoTmpDir();
                        rootTmpDir = javaIoTmpDir;
                    }
                    return FileUtils.canonicalize(new File(rootTmpDir));
                });
                return new ConstantTestKitDirProvider(temporaryFileProvider.newTemporaryFile(".gradle-test-kit"));
            }
        });
    }

    public TestKitDirProvider getTestKitDirProvider() {
        return testKitDirProvider;
    }

    @Override
    public GradleRunner withGradleVersion(String versionNumber) {
        this.gradleProvider = GradleProvider.version(versionNumber);
        return this;
    }

    @Override
    public GradleRunner withGradleInstallation(File installation) {
        this.gradleProvider = GradleProvider.installation(installation);
        return this;
    }

    @Override
    public GradleRunner withGradleDistribution(URI distribution) {
        this.gradleProvider = GradleProvider.uri(distribution);
        return this;
    }

    @Override
    public DefaultGradleRunner withTestKitDir(File testKitDir) {
        validateArgumentNotNull(testKitDir, "testKitDir");
        this.testKitDirProvider = new ConstantTestKitDirProvider(testKitDir);
        return this;
    }

    public List<String> getJvmArguments() {
        return jvmArguments;
    }

    public DefaultGradleRunner withJvmArguments(List<String> jvmArguments) {
        this.jvmArguments = Collections.unmodifiableList(new ArrayList<>(jvmArguments));
        return this;
    }

    public DefaultGradleRunner withJvmArguments(String... jvmArguments) {
        return withJvmArguments(Arrays.asList(jvmArguments));
    }

    @Override
    public File getProjectDir() {
        return projectDirectory;
    }

    @Override
    public DefaultGradleRunner withProjectDir(File projectDir) {
        this.projectDirectory = projectDir;
        return this;
    }

    @Override
    public List<String> getArguments() {
        return arguments;
    }

    @Override
    public DefaultGradleRunner withArguments(List<String> arguments) {
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        return this;
    }

    @Override
    public DefaultGradleRunner withArguments(String... arguments) {
        return withArguments(Arrays.asList(arguments));
    }

    @Override
    public List<? extends File> getPluginClasspath() {
        return classpath.getAsFiles();
    }

    @Override
    public GradleRunner withPluginClasspath() {
        this.classpath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath());
        return this;
    }

    @Override
    public GradleRunner withPluginClasspath(Iterable<? extends File> classpath) {
        List<File> f = new ArrayList<>();
        for (File file : classpath) {
            // These objects are going across the wire.
            // 1. Convert any subclasses back to File in case the subclass isn't available in Gradle.
            // 2. Make them absolute here to deal with a different root at the server
            f.add(new File(file.getAbsolutePath()));
        }
        if (!f.isEmpty()) {
            this.classpath = DefaultClassPath.of(f);
        }
        return this;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    @Override
    public GradleRunner withDebug(boolean flag) {
        this.debug = flag;
        return this;
    }

    @Override
    public Map<String, String> getEnvironment() {
        return environment;
    }

    @Override
    public GradleRunner withEnvironment(Map<String, String> environment) {
        this.environment = environment;
        return this;
    }

    @Override
    public GradleRunner forwardStdOutput(Writer writer) {
        if (forwardingSystemStreams) {
            forwardingSystemStreams = false;
            this.standardError = null;
        }
        validateArgumentNotNull(writer, "standardOutput");
        this.standardOutput = toOutputStream(writer);
        return this;
    }

    @Override
    public GradleRunner forwardStdError(Writer writer) {
        if (forwardingSystemStreams) {
            forwardingSystemStreams = false;
            this.standardOutput = null;
        }
        validateArgumentNotNull(writer, "standardError");
        this.standardError = toOutputStream(writer);
        return this;
    }

    @Override
    public GradleRunner forwardOutput() {
        forwardingSystemStreams = true;
        OutputStream systemOut = new SynchronizedOutputStream(System.out);
        this.standardOutput = systemOut;
        this.standardError = systemOut;
        return this;
    }

    public GradleRunner withStandardInput(InputStream standardInput) {
        this.standardInput = standardInput;
        return this;
    }

    private static OutputStream toOutputStream(Writer standardOutput) {
        return new WriterOutputStream(standardOutput, Charset.defaultCharset());
    }

    private void validateArgumentNotNull(Object argument, String argumentName) {
        if (argument == null) {
            throw new IllegalArgumentException(String.format("%s argument cannot be null", argumentName));
        }
    }

    @Override
    public BuildResult build() {
        return run(gradleExecutionResult -> {
            if (!gradleExecutionResult.isSuccessful()) {
                throw new UnexpectedBuildFailure(createDiagnosticsMessage("Unexpected build execution failure", gradleExecutionResult), createBuildResult(gradleExecutionResult));
            }
        });
    }

    @Override
    public BuildResult buildAndFail() {
        return run(gradleExecutionResult -> {
            if (gradleExecutionResult.isSuccessful()) {
                throw new UnexpectedBuildSuccess(createDiagnosticsMessage("Unexpected build execution success", gradleExecutionResult), createBuildResult(gradleExecutionResult));
            }
        });
    }

    @Override
    public BuildResult run() {
        return run(gradleExecutionResult -> {
        });
    }

    String createDiagnosticsMessage(String trailingMessage, GradleExecutionResult gradleExecutionResult) {
        String lineBreak = SystemProperties.getInstance().getLineSeparator();
        StringBuilder message = new StringBuilder();
        message.append(trailingMessage);
        message.append(" in ");
        message.append(getProjectDir().getAbsolutePath());
        message.append(" with arguments ");
        message.append(getArguments());

        String output = gradleExecutionResult.getOutput();
        if (output != null && !output.isEmpty()) {
            message.append(lineBreak);
            message.append(lineBreak);
            message.append("Output:");
            message.append(lineBreak);
            message.append(output);
        }

        return message.toString();
    }

    private BuildResult run(Action<GradleExecutionResult> resultVerification) {
        if (projectDirectory == null) {
            throw new InvalidRunnerConfigurationException("Please specify a project directory before executing the build");
        }

        if (environment != null && debug) {
            throw new InvalidRunnerConfigurationException("Debug mode is not allowed when environment variables are specified. " +
                "Debug mode runs 'in process' but we need to fork a separate process to pass environment variables. " +
                "To run with debug mode, please remove environment variables.");
        }

        File testKitDir = createTestKitDir(testKitDirProvider);

        GradleProvider effectiveDistribution = gradleProvider == null ? findGradleInstallFromGradleRunner() : gradleProvider;

        List<String> effectiveArguments = new ArrayList<>();
        Map<String, String> effectiveEnvironment = new HashMap<>();

        if (OperatingSystem.current().isWindows()) {
            // When using file system watching in Windows tests it becomes harder to delete the project directory,
            // since file system watching on Windows adds a lock on the watched directory, which is currently the project directory.
            // After deleting the contents of the watched directory, Gradle will stop watching the directory and release the file lock.
            // That may require a retry to delete the watched directory.
            // To avoid those problems for TestKit tests on Windows, we disable file system watching there.
            effectiveArguments.add("-D" + StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY + "=false");
            // Without the SystemRoot environment variable been defined, Gradle Runner doesn't work on Windows when requiring network
            // connections.
            effectiveEnvironment.put("SystemRoot", System.getenv("SystemRoot"));
        }

        effectiveArguments.addAll(arguments);
        if (environment != null) {
            effectiveEnvironment.putAll(environment);
        } else {
            // environment can be null, which means that all the existing defined environment variables are used instead
            effectiveEnvironment = null;
        }

        GradleExecutionResult execResult = gradleExecutor.run(new GradleExecutionParameters(
            effectiveDistribution,
            testKitDir,
            projectDirectory,
            effectiveArguments,
            jvmArguments,
            classpath,
            debug,
            standardOutput,
            standardError,
            standardInput,
            effectiveEnvironment
        ));

        resultVerification.execute(execResult);
        return createBuildResult(execResult);
    }

    private BuildResult createBuildResult(GradleExecutionResult execResult) {
        return new FeatureCheckBuildResult(
            execResult.getBuildOperationParameters(),
            execResult.getOutput(),
            execResult.getTasks()
        );
    }

    private File createTestKitDir(TestKitDirProvider testKitDirProvider) {
        File dir = testKitDirProvider.getDir();
        if (dir.isDirectory()) {
            if (!dir.canWrite()) {
                throw new InvalidRunnerConfigurationException("Unable to write to test kit directory: " + dir.getAbsolutePath());
            }
            return dir;
        } else if (dir.exists() && !dir.isDirectory()) {
            throw new InvalidRunnerConfigurationException("Unable to use non-directory as test kit directory: " + dir.getAbsolutePath());
        } else if (dir.mkdirs() || dir.isDirectory()) {
            return dir;
        } else {
            throw new InvalidRunnerConfigurationException("Unable to create test kit directory: " + dir.getAbsolutePath());
        }
    }

    private static GradleProvider findGradleInstallFromGradleRunner() {
        GradleInstallation gradleInstallation = CurrentGradleInstallation.get();
        if (gradleInstallation == null) {
            if ("embedded".equals(System.getProperty("org.gradle.integtest.executer"))) {
                return GradleProvider.embedded();
            }
            String messagePrefix = "Could not find a Gradle installation to use based on the location of the GradleRunner class";
            try {
                File classpathForClass = ClasspathUtil.getClasspathForClass(GradleRunner.class);
                messagePrefix += ": " + classpathForClass.getAbsolutePath();
            } catch (Exception ignore) {
                // ignore
            }
            throw new InvalidRunnerConfigurationException(messagePrefix + ". Please specify a Gradle runtime to use via GradleRunner.withGradleVersion() or similar.");
        }
        return GradleProvider.installation(gradleInstallation.getGradleHome());
    }


}
