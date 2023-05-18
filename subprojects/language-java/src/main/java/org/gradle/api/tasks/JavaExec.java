/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks;

import org.apache.tools.ant.types.Commandline;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.internal.JavaExecExecutableUtils;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultJavaExecSpec;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.JavaExecAction;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

/**
 * Executes a Java application in a child process.
 * <p>
 * Similar to {@link Exec}, but starts a JVM with the given classpath and application class.
 * </p>
 * <pre class='autoTested'>
 * plugins {
 *     id 'java'
 * }
 *
 * task runApp(type: JavaExec) {
 *   classpath = sourceSets.main.runtimeClasspath
 *
 *   mainClass = 'package.Main'
 *
 *   // arguments to pass to the application
 *   args 'appArg1'
 * }
 *
 * // Using and creating an Executable Jar
 * jar {
 *   manifest {
 *     attributes('Main-Class': 'package.Main')
 *   }
 * }
 *
 * task runExecutableJar(type: JavaExec) {
 *   // Executable jars can have only _one_ jar on the classpath.
 *   classpath = files(tasks.jar)
 *
 *   // 'main' does not need to be specified
 *
 *   // arguments to pass to the application
 *   args 'appArg1'
 * }
 *
 * </pre>
 * <p>
 * The process can be started in debug mode (see {@link #getDebug()}) in an ad-hoc manner by supplying the `--debug-jvm` switch when invoking the build.
 * <pre>
 * gradle someJavaExecTask --debug-jvm
 * </pre>
 * <p>
 * Also, debug configuration can be explicitly set in {@link #debugOptions(Action)}:
 * <pre>
 * task runApp(type: JavaExec) {
 *    ...
 *
 *    debugOptions {
 *        enabled = true
 *        port = 5566
 *        server = true
 *        suspend = false
 *    }
 * }
 * </pre>
 */
@DisableCachingByDefault(because = "Gradle would require more information to cache this task")
public abstract class JavaExec extends ConventionTask implements JavaExecSpec {

    private final DefaultJavaExecSpec javaExecSpec;
    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final ModularitySpec modularity;
    private final Property<ExecResult> execResult;
    private final Property<JavaLauncher> javaLauncher;
    private final ListProperty<String> jvmArguments;

    public JavaExec() {
        ObjectFactory objectFactory = getObjectFactory();
        mainModule = objectFactory.property(String.class);
        mainClass = objectFactory.property(String.class);
        modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        execResult = objectFactory.property(ExecResult.class);
        javaExecSpec = objectFactory.newInstance(DefaultJavaExecSpec.class);

        Provider<Iterable<String>> jvmArgumentsConvention = getProviderFactory().provider(this::jvmArgsConventionValue);

        jvmArguments = objectFactory.listProperty(String.class).convention(jvmArgumentsConvention);

        javaExecSpec.getMainClass().convention(mainClass);
        javaExecSpec.getMainModule().convention(mainModule);
        javaExecSpec.getModularity().getInferModulePath().convention(modularity.getInferModulePath());

        JavaToolchainService javaToolchainService = getJavaToolchainService();
        Provider<JavaLauncher> javaLauncherConvention = getProviderFactory()
            .provider(() -> JavaExecExecutableUtils.getExecutableOverrideToolchainSpec(this, objectFactory))
            .flatMap(javaToolchainService::launcherFor)
            .orElse(javaToolchainService.launcherFor(it -> {}));
        javaLauncher = objectFactory.property(JavaLauncher.class).convention(javaLauncherConvention);
        javaLauncher.finalizeValueOnRead();
    }

    @TaskAction
    public void exec() {
        validateExecutableMatchesToolchain();

        List<String> jvmArgs = jvmArguments.getOrNull();
        if (jvmArgs != null) {
            javaExecSpec.setExtraJvmArgs(jvmArgs);
        }

        JavaExecAction javaExecAction = getExecActionFactory().newJavaExecAction();
        javaExecSpec.copyTo(javaExecAction);
        String effectiveExecutable = getJavaLauncher().get().getExecutablePath().toString();
        javaExecAction.setExecutable(effectiveExecutable);

        execResult.set(javaExecAction.execute());
    }

    private void validateExecutableMatchesToolchain() {
        File toolchainExecutable = getJavaLauncher().get().getExecutablePath().getAsFile();
        String customExecutable = getExecutable();
        JavaExecutableUtils.validateExecutable(
            customExecutable, "Toolchain from `executable` property",
            toolchainExecutable, "toolchain from `javaLauncher` property");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllJvmArgs() {
        return javaExecSpec.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllJvmArgs(List<String> arguments) {
        javaExecSpec.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        javaExecSpec.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getJvmArgs() {
        return jvmArguments.getOrNull();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJvmArgs(List<String> arguments) {
        jvmArguments.empty();
        jvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJvmArgs(Iterable<?> arguments) {
        jvmArguments.empty();
        jvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec jvmArgs(Iterable<?> arguments) {
        addJvmArguments(arguments);
        javaExecSpec.checkDebugConfiguration(arguments);
        return this;
    }

    private void addJvmArguments(Iterable<?> arguments) {
        for (Object arg : arguments) {
            jvmArguments.add(arg.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec jvmArgs(Object... arguments) {
        jvmArgs(Arrays.asList(arguments));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getSystemProperties() {
        return javaExecSpec.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        javaExecSpec.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec systemProperties(Map<String, ?> properties) {
        javaExecSpec.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec systemProperty(String name, Object value) {
        javaExecSpec.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileCollection getBootstrapClasspath() {
        return javaExecSpec.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBootstrapClasspath(FileCollection classpath) {
        javaExecSpec.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec bootstrapClasspath(Object... classpath) {
        javaExecSpec.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMinHeapSize() {
        return javaExecSpec.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinHeapSize(String heapSize) {
        javaExecSpec.setMinHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultCharacterEncoding() {
        return javaExecSpec.getDefaultCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        javaExecSpec.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMaxHeapSize() {
        return javaExecSpec.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxHeapSize(String heapSize) {
        javaExecSpec.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getEnableAssertions() {
        return javaExecSpec.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnableAssertions(boolean enabled) {
        javaExecSpec.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getDebug() {
        return javaExecSpec.getDebug();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Option(option = "debug-jvm", description = "Enable debugging for the process. The process is started suspended and listening on port 5005.")
    public void setDebug(boolean enabled) {
        javaExecSpec.setDebug(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaDebugOptions getDebugOptions() {
        return javaExecSpec.getDebugOptions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        javaExecSpec.debugOptions(action);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Property<String> getMainModule() {
        return mainModule;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Property<String> getMainClass() {
        return mainClass;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getArgs() {
        return javaExecSpec.getArgs();
    }

    /**
     * Parses an argument list from {@code args} and passes it to {@link #setArgs(List)}.
     *
     * <p>
     * The parser supports both single quote ({@code '}) and double quote ({@code "}) as quote delimiters.
     * For example, to pass the argument {@code foo bar}, use {@code "foo bar"}.
     * </p>
     * <p>
     * Note: the parser does <strong>not</strong> support using backslash to escape quotes. If this is needed,
     * use the other quote delimiter around it.
     * For example, to pass the argument {@code 'singly quoted'}, use {@code "'singly quoted'"}.
     * </p>
     *
     * @param args Args for the main class. Will be parsed into an argument list.
     * @return this
     * @since 4.9
     */
    @Option(option = "args", description = "Command line arguments passed to the main class.")
    public JavaExec setArgsString(String args) {
        return setArgs(Arrays.asList(Commandline.translateCommandline(args)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec setArgs(List<String> applicationArgs) {
        javaExecSpec.setArgs(applicationArgs);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec setArgs(Iterable<?> applicationArgs) {
        javaExecSpec.setArgs(applicationArgs);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec args(Object... args) {
        javaExecSpec.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExecSpec args(Iterable<?> args) {
        javaExecSpec.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return javaExecSpec.getArgumentProviders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec setClasspath(FileCollection classpath) {
        javaExecSpec.setClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec classpath(Object... paths) {
        javaExecSpec.classpath(paths);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileCollection getClasspath() {
        return javaExecSpec.getClasspath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModularitySpec getModularity() {
        return modularity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec copyTo(JavaForkOptions options) {
        javaExecSpec.copyTo(options);
        return this;
    }

    /**
     * Returns the version of the Java executable specified by {@link #getJavaLauncher()}.
     *
     * @since 5.2
     */
    @Input
    public JavaVersion getJavaVersion() {
        return JavaVersion.toVersion(getJavaLauncher().get().getMetadata().getLanguageVersion().asInt());
    }

    /**
     * {@inheritDoc}
     */
    @Internal("covered by getJavaVersion")
    @Nullable
    @Override
    public String getExecutable() {
        return javaExecSpec.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExecutable(String executable) {
        javaExecSpec.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExecutable(Object executable) {
        javaExecSpec.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec executable(Object executable) {
        javaExecSpec.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public File getWorkingDir() {
        return javaExecSpec.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(File dir) {
        javaExecSpec.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(Object dir) {
        javaExecSpec.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec workingDir(Object dir) {
        javaExecSpec.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public Map<String, Object> getEnvironment() {
        return javaExecSpec.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        javaExecSpec.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec environment(String name, Object value) {
        javaExecSpec.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec environment(Map<String, ?> environmentVariables) {
        javaExecSpec.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec copyTo(ProcessForkOptions target) {
        javaExecSpec.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec setStandardInput(InputStream inputStream) {
        javaExecSpec.setStandardInput(inputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public InputStream getStandardInput() {
        return javaExecSpec.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec setStandardOutput(OutputStream outputStream) {
        javaExecSpec.setStandardOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public OutputStream getStandardOutput() {
        return javaExecSpec.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExec setErrorOutput(OutputStream outputStream) {
        javaExecSpec.setErrorOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public OutputStream getErrorOutput() {
        return javaExecSpec.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JavaExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        javaExecSpec.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Input
    public boolean isIgnoreExitValue() {
        return javaExecSpec.isIgnoreExitValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public List<String> getCommandLine() {
        return javaExecSpec.getCommandLine();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return javaExecSpec.getJvmArgumentProviders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ListProperty<String> getJvmArguments() {
        return jvmArguments;
    }

    /**
     * Returns the result for the command run by this task. The provider has no value if this task has not been executed yet.
     *
     * @return A provider of the result.
     * @since 6.1
     */
    @Internal
    public Provider<ExecResult> getExecutionResult() {
        return execResult;
    }

    /**
     * Configures the java executable to be used to run the tests.
     *
     * @since 6.7
     */
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ExecActionFactory getExecActionFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProviderFactory getProviderFactory() {
        throw new UnsupportedOperationException();
    }

    private Iterable<String> jvmArgsConventionValue() {
        Iterable<String> jvmArgs = getConventionMapping().getConventionValue(null, "jvmArgs", false);
        return jvmArgs != null ? jvmArgs : emptyList();
    }
}
