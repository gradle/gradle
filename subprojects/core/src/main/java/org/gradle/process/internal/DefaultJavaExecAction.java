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
import org.gradle.api.file.FileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
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
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Use {@link ExecActionFactory} (for core code) or {@link org.gradle.process.ExecOperations} (for plugin code) instead.
 *
 * TODO: We should remove setters and have abstract getters in Gradle 9.0 and configure builder in execute() method.
 */
public class DefaultJavaExecAction implements JavaExecAction {

    private final JavaExecHandleBuilder javaExecHandleBuilder;
    private final Property<Boolean> ignoreExitValue;
    private final Property<InputStream> standardInput;
    private final Property<OutputStream> standardOutput;
    private final Property<OutputStream> errorOutput;

    @Inject
    public DefaultJavaExecAction(ObjectFactory objectFactory, JavaExecHandleBuilder javaExecHandleBuilder) {
        this.javaExecHandleBuilder = javaExecHandleBuilder;
        this.ignoreExitValue = objectFactory.property(Boolean.class).convention(false);
        this.standardInput = objectFactory.property(InputStream.class);
        this.standardOutput = objectFactory.property(OutputStream.class);
        this.errorOutput = objectFactory.property(OutputStream.class);
    }

    @Override
    public ExecResult execute() {
        if (getStandardInput().isPresent()) {
            javaExecHandleBuilder.setStandardInput(getStandardInput().get());
        }
        if (getStandardOutput().isPresent()) {
            javaExecHandleBuilder.setStandardOutput(getStandardOutput().get());
        }
        if (getErrorOutput().isPresent()) {
            javaExecHandleBuilder.setErrorOutput(getErrorOutput().get());
        }

        ExecHandle execHandle = javaExecHandleBuilder.build();
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
    public Property<Boolean> getIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public Property<InputStream> getStandardInput() {
        return standardInput;
    }

    @Override
    public Property<OutputStream> getStandardOutput() {
        return standardOutput;
    }

    @Override
    public Property<OutputStream> getErrorOutput() {
        return errorOutput;
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
    public String getExecutable() {
        return javaExecHandleBuilder.getExecutable();
    }

    @Override
    public void setExecutable(String executable) {
        javaExecHandleBuilder.setExecutable(executable);
    }

    @Override
    public void setExecutable(Object executable) {
        javaExecHandleBuilder.setExecutable(executable);
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        javaExecHandleBuilder.setExecutable(executable);
        return this;
    }

    @Override
    public File getWorkingDir() {
        return javaExecHandleBuilder.getWorkingDir();
    }

    @Override
    public void setWorkingDir(File dir) {
        javaExecHandleBuilder.setWorkingDir(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        javaExecHandleBuilder.setWorkingDir(dir);
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        javaExecHandleBuilder.setWorkingDir(dir);
        return this;
    }

    @Override
    public Map<String, Object> getEnvironment() {
        return javaExecHandleBuilder.getEnvironment();
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        javaExecHandleBuilder.setEnvironment(environmentVariables);
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        javaExecHandleBuilder.environment(environmentVariables);
        return this;
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        javaExecHandleBuilder.environment(name, value);
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
