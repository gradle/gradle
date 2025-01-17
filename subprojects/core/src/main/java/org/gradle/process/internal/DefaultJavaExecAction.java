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
import org.gradle.api.file.FileCollection;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;

import javax.annotation.Nullable;
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
    private boolean ignoreExitValue;

    public DefaultJavaExecAction(JavaExecHandleBuilder javaExecHandleBuilder) {
        this.javaExecHandleBuilder = javaExecHandleBuilder;
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = javaExecHandleBuilder.build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!ignoreExitValue) {
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
    public BaseExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    @Override
    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public BaseExecSpec setStandardInput(InputStream inputStream) {
        javaExecHandleBuilder.setStandardInput(inputStream);
        return this;
    }

    @Override
    public InputStream getStandardInput() {
        return javaExecHandleBuilder.getStandardInput();
    }

    @Override
    public BaseExecSpec setStandardOutput(OutputStream outputStream) {
        javaExecHandleBuilder.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public OutputStream getStandardOutput() {
        return javaExecHandleBuilder.getStandardOutput();
    }

    @Override
    public BaseExecSpec setErrorOutput(OutputStream outputStream) {
        javaExecHandleBuilder.setErrorOutput(outputStream);
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return javaExecHandleBuilder.getErrorOutput();
    }

    @Override
    public List<String> getCommandLine() {
        return javaExecHandleBuilder.getCommandLine();
    }

    @Override
    public Map<String, Object> getSystemProperties() {
        return javaExecHandleBuilder.getSystemProperties();
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        javaExecHandleBuilder.setSystemProperties(properties);
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
    public String getDefaultCharacterEncoding() {
        return javaExecHandleBuilder.getDefaultCharacterEncoding();
    }

    @Override
    public void setDefaultCharacterEncoding(@Nullable String defaultCharacterEncoding) {
        javaExecHandleBuilder.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Nullable
    @Override
    public String getMinHeapSize() {
        return javaExecHandleBuilder.getMinHeapSize();
    }

    @Override
    public void setMinHeapSize(@Nullable String heapSize) {
        javaExecHandleBuilder.setMinHeapSize(heapSize);
    }

    @Nullable
    @Override
    public String getMaxHeapSize() {
        return javaExecHandleBuilder.getMaxHeapSize();
    }

    @Override
    public void setMaxHeapSize(@Nullable String heapSize) {
        javaExecHandleBuilder.setMaxHeapSize(heapSize);
    }

    @Nullable
    @Override
    public List<String> getJvmArgs() {
        return javaExecHandleBuilder.getJvmArgs();
    }

    @Override
    public void setJvmArgs(@Nullable List<String> arguments) {
        javaExecHandleBuilder.setJvmArgs(arguments);
    }

    @Override
    public void setJvmArgs(@Nullable Iterable<?> arguments) {
        javaExecHandleBuilder.setJvmArgs(arguments);
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
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return javaExecHandleBuilder.getJvmArgumentProviders();
    }

    @Override
    public FileCollection getBootstrapClasspath() {
        return javaExecHandleBuilder.getBootstrapClasspath();
    }

    @Override
    public void setBootstrapClasspath(FileCollection classpath) {
        javaExecHandleBuilder.setBootstrapClasspath(classpath);
    }

    @Override
    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        javaExecHandleBuilder.bootstrapClasspath(classpath);
        return this;
    }

    @Override
    public boolean getEnableAssertions() {
        return javaExecHandleBuilder.getEnableAssertions();
    }

    @Override
    public void setEnableAssertions(boolean enabled) {
        javaExecHandleBuilder.setEnableAssertions(enabled);
    }

    @Override
    public boolean getDebug() {
        return javaExecHandleBuilder.getDebug();
    }

    @Override
    public void setDebug(boolean enabled) {
        javaExecHandleBuilder.setDebug(enabled);
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
    public List<String> getAllJvmArgs() {
        return javaExecHandleBuilder.getAllJvmArgs();
    }

    @Override
    public void setAllJvmArgs(List<String> arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        throw new UnsupportedOperationException();
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
