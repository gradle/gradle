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
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
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

    private final JavaExecSpec javaExecSpec;
    private final JavaExecHandleBuilder javaExecHandleBuilder;

    @Inject
    public DefaultJavaExecAction(JavaExecSpec javaExecSpec, JavaExecHandleBuilder javaExecHandleBuilder) {
        this.javaExecSpec = javaExecSpec;
        this.javaExecHandleBuilder = javaExecHandleBuilder;
    }

    @Override
    public ExecResult execute() {
        // Jvm properties are delegated to JavaExecHandleBuilder so getAllJvmArgs() can return the correct values
        // and properties from BaseExecSpec are delegated to JavaExecSpec, so here we have to copy them to JavaExecHandleBuilder
        // TODO: We should probably delegate all properties just to JavaExecSpec and
        //  build new JavaExecHandleBuilder here and make JavaExecHandleBuilder Provider API agnostic
        ExecHandle execHandle = javaExecHandleBuilder
            .configureFrom(javaExecSpec)
            .build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }

    @Override
    public ListProperty<String> getJvmArguments() {
        return javaExecHandleBuilder.getJvmArguments();
    }

    @Override
    public Property<String> getMainModule() {
        return javaExecHandleBuilder.getMainModule();
    }

    @Override
    public Property<String> getMainClass() {
        return javaExecHandleBuilder.getMainClass();
    }

    public void setExtraJvmArgs(List<String> jvmArgs) {
        javaExecHandleBuilder.setExtraJvmArgs(jvmArgs);
    }

    @Nullable
    @Override
    public List<String> getArgs() {
        return javaExecHandleBuilder.getArgs();
    }

    @Override
    public JavaExecSpec args(Object... args) {
        javaExecHandleBuilder.args(args);
        return this;
    }

    @Override
    public JavaExecSpec args(Iterable<?> args) {
        javaExecHandleBuilder.args(args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable List<String> args) {
        javaExecHandleBuilder.setArgs(args);
        return this;
    }

    @Override
    public JavaExecSpec setArgs(@Nullable Iterable<?> args) {
        javaExecHandleBuilder.setArgs(args);
        return this;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return javaExecHandleBuilder.getArgumentProviders();
    }

    @Override
    public JavaExecSpec classpath(Object... paths) {
        javaExecHandleBuilder.classpath(paths);
        return this;
    }

    @Override
    public FileCollection getClasspath() {
        return javaExecHandleBuilder.getClasspath();
    }

    @Override
    public JavaExecSpec setClasspath(FileCollection classpath) {
        javaExecHandleBuilder.setClasspath(classpath);
        return this;
    }

    @Override
    public ModularitySpec getModularity() {
        return javaExecHandleBuilder.getModularity();
    }

    @Override
    public List<String> getCommandLine() {
        return javaExecHandleBuilder.getCommandLine();
    }

    @Override
    public MapProperty<String, Object> getSystemProperties() {
        return javaExecHandleBuilder.getSystemProperties();
    }

    @Override
    public JavaForkOptions systemProperties(Map<String, ?> properties) {
        javaExecHandleBuilder.systemProperties(properties);
        return this;
    }

    @Override
    public JavaForkOptions systemProperty(String name, Object value) {
        javaExecHandleBuilder.systemProperty(name, value);
        return this;
    }

    @Nullable
    @Override
    public Property<String> getDefaultCharacterEncoding() {
        return javaExecHandleBuilder.getDefaultCharacterEncoding();
    }

    @Nullable
    @Override
    public Property<String> getMinHeapSize() {
        return javaExecHandleBuilder.getMinHeapSize();
    }

    @Nullable
    @Override
    public Property<String> getMaxHeapSize() {
        return javaExecHandleBuilder.getMaxHeapSize();
    }

    @Nullable
    @Override
    public ListProperty<String> getJvmArgs() {
        return javaExecHandleBuilder.getJvmArgs();
    }

    @Override
    public JavaForkOptions jvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.jvmArgs(arguments);
        return this;
    }

    @Override
    public JavaForkOptions jvmArgs(Object... arguments) {
        javaExecHandleBuilder.jvmArgs(arguments);
        return this;
    }

    @Override
    public ListProperty<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return javaExecHandleBuilder.getJvmArgumentProviders();
    }

    @Override
    public ConfigurableFileCollection getBootstrapClasspath() {
        return javaExecHandleBuilder.getBootstrapClasspath();
    }

    @Override
    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        javaExecHandleBuilder.bootstrapClasspath(classpath);
        return this;
    }

    @Override
    public Property<Boolean> getEnableAssertions() {
        return javaExecHandleBuilder.getEnableAssertions();
    }

    @Override
    public Property<Boolean> getDebug() {
        return javaExecHandleBuilder.getDebug();
    }

    @Override
    public JavaDebugOptions getDebugOptions() {
        return javaExecHandleBuilder.getDebugOptions();
    }

    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        javaExecHandleBuilder.debugOptions(action);
    }

    @Override
    public Provider<List<String>> getAllJvmArgs() {
        return javaExecHandleBuilder.getAllJvmArgs();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaForkOptions copyTo(JavaForkOptions options) {
        throw new UnsupportedOperationException();
    }
}
