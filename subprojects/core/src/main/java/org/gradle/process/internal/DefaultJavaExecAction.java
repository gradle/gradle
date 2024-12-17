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

package org.gradle.process.internal;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Use {@link ExecActionFactory} (for core code) or {@link org.gradle.process.ExecOperations} (for plugin code) instead.
 */
public class DefaultJavaExecAction implements JavaExecAction {

    private final DefaultJavaExecSpec javaExecSpec;
    private final JavaExecHandleBuilder javaExecHandleBuilder;
    @Nullable
    private final JavaModuleDetector javaModuleDetector;

    @Inject
    public DefaultJavaExecAction(
        DefaultJavaExecSpec javaExecSpec,
        JavaExecHandleBuilder javaExecHandleBuilder
    ) {
        this.javaExecSpec = javaExecSpec;
        this.javaExecHandleBuilder = javaExecHandleBuilder;
        this.javaModuleDetector = javaExecHandleBuilder.getJavaModuleDetector();
        // JavaExecHandleBuilder has default java executable set
        getExecutable().set(javaExecHandleBuilder.getExecutable());
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = javaExecHandleBuilder
            // TODO: Get rid of extraJvmArgs
            .setExtraJvmArgs(javaExecSpec.getExtraJvmArgs())
            .configureFrom(this)
            .build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }

    @Override
    public ListProperty<String> getJvmArguments() {
        return javaExecSpec.getJvmArguments();
    }

    @Override
    public Property<String> getMainModule() {
        return javaExecSpec.getMainModule();
    }

    @Override
    public Property<String> getMainClass() {
        return javaExecSpec.getMainClass();
    }

    public void setExtraJvmArgs(List<String> jvmArgs) {
        javaExecSpec.setExtraJvmArgs(jvmArgs);
    }

    @Nullable
    @Override
    public List<String> getArgs() {
        return javaExecSpec.getArgs();
    }

    @Override
    public JavaExecSpec args(Object... args) {
        javaExecSpec.args(args);
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        javaExecSpec.args(args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable List<String> args) {
        javaExecSpec.setArgs(args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable Iterable<?> args) {
        javaExecSpec.setArgs(args);
        return this;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return javaExecSpec.getArgumentProviders();
    }

    @Override
    public JavaExecSpec classpath(Object... paths) {
        javaExecSpec.classpath(paths);
        return this;
    }

    @Override
    public FileCollection getClasspath() {
        return javaExecSpec.getClasspath();
    }

    @Override
    public JavaExecSpec setClasspath(FileCollection classpath) {
        javaExecSpec.setClasspath(classpath);
        return this;
    }

    @Override
    public ModularitySpec getModularity() {
        return javaExecSpec.getModularity();
    }

    @Override
    public Provider<List<String>> getCommandLine() {
        return javaExecSpec.getExecutable().zip(getAllJvmArgs(), (SerializableLambdas.SerializableBiFunction<String, List<String>, List<String>>) (executable, allJvmArgs) -> {
            List<String> allArgs = ExecHandleCommandLineCombiner.getAllArgs(allJvmArgs, getArgs(), getArgumentProviders());
            return ExecHandleCommandLineCombiner.getCommandLine(executable, allArgs);
        });
    }

    @Override
    public MapProperty<String, Object> getSystemProperties() {
        return javaExecSpec.getSystemProperties();
    }

    @Override
    public JavaForkOptions systemProperties(Map<String, ?> properties) {
        javaExecSpec.systemProperties(properties);
        return this;
    }

    @Override
    public JavaForkOptions systemProperty(String name, Object value) {
        javaExecSpec.systemProperty(name, value);
        return this;
    }

    @Nullable
    @Override
    public Property<String> getDefaultCharacterEncoding() {
        return javaExecSpec.getDefaultCharacterEncoding();
    }

    @Nullable
    @Override
    public Property<String> getMinHeapSize() {
        return javaExecSpec.getMinHeapSize();
    }

    @Nullable
    @Override
    public Property<String> getMaxHeapSize() {
        return javaExecSpec.getMaxHeapSize();
    }

    @Nullable
    @Override
    public ListProperty<String> getJvmArgs() {
        return javaExecSpec.getJvmArgs();
    }

    @Override
    public JavaForkOptions jvmArgs(Iterable<?> arguments) {
        javaExecSpec.jvmArgs(arguments);
        return this;
    }

    @Override
    public JavaForkOptions jvmArgs(Object... arguments) {
        javaExecSpec.jvmArgs(arguments);
        return this;
    }

    @Override
    public ListProperty<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return javaExecSpec.getJvmArgumentProviders();
    }

    @Override
    public ConfigurableFileCollection getBootstrapClasspath() {
        return javaExecSpec.getBootstrapClasspath();
    }

    @Override
    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        javaExecSpec.bootstrapClasspath(classpath);
        return this;
    }

    @Override
    public Property<Boolean> getEnableAssertions() {
        return javaExecSpec.getEnableAssertions();
    }

    @Override
    public Property<Boolean> getDebug() {
        return javaExecSpec.getDebug();
    }

    @Override
    public JavaDebugOptions getDebugOptions() {
        return javaExecSpec.getDebugOptions();
    }

    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        javaExecSpec.debugOptions(action);
    }

    @Override
    public Provider<List<String>> getAllJvmArgs() {
        return javaExecSpec.getAllJvmArgs().map(allJvmArgs -> ExecHandleCommandLineCombiner.getAllJvmArgs(
            allJvmArgs,
            javaExecSpec.getClasspath(),
            javaExecSpec.getMainClass(),
            javaExecSpec.getMainModule(),
            javaExecSpec.getModularity(),
            javaModuleDetector
        ));
    }

    @Override
    public Property<Boolean> getIgnoreExitValue() {
        return javaExecSpec.getIgnoreExitValue();
    }

    @Override
    public Property<InputStream> getStandardInput() {
        return javaExecSpec.getStandardInput();
    }

    @Override
    public Property<OutputStream> getStandardOutput() {
        return javaExecSpec.getStandardOutput();
    }

    @Override
    public Property<OutputStream> getErrorOutput() {
        return javaExecSpec.getErrorOutput();
    }

    @Override
    public Property<String> getExecutable() {
        return javaExecSpec.getExecutable();
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        javaExecSpec.executable(executable);
        return this;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return javaExecSpec.getWorkingDir();
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        javaExecSpec.workingDir(dir);
        return this;
    }

    @Override
    public MapProperty<String, Object> getEnvironment() {
        return javaExecSpec.getEnvironment();
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        javaExecSpec.environment(environmentVariables);
        return this;
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        javaExecSpec.environment(name, value);
        return this;
    }

    @Override
    public JavaExecAction listener(ExecHandleListener listener) {
        javaExecHandleBuilder.listener(listener);
        return this;
    }


    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions options) {
        javaExecSpec.copyTo(options);
        return this;
    }

    @Override
    public JavaForkOptions copyTo(JavaForkOptions options) {
        javaExecSpec.copyTo(options);
        return this;
    }
}
