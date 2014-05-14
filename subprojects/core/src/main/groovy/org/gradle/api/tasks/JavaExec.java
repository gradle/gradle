/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.process.JavaExecSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultJavaExecAction;
import org.gradle.process.internal.JavaExecAction;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Executes a Java application in a child process.
 * <p>
 * The process can be started in debug mode (see {@link #getDebug()}) in an ad-hoc manner by supplying the `--debug-jvm` switch when invoking the build.
 * <pre>
 * gradle someJavaExecTask --debug-jvm
 * </pre>
 */
public class JavaExec extends ConventionTask implements JavaExecSpec {
    private JavaExecAction javaExecHandleBuilder;

    public JavaExec() {
        javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver());
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void exec() {
        setMain(getMain()); // make convention mapping work (at least for 'main'...
        setJvmArgs(getJvmArgs()); // ...and for 'jvmArgs')
        javaExecHandleBuilder.execute();
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getAllJvmArgs() {
        return javaExecHandleBuilder.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setAllJvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getJvmArgs() {
        return javaExecHandleBuilder.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setJvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec jvmArgs(Iterable<?> arguments) {
        javaExecHandleBuilder.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec jvmArgs(Object... arguments) {
        javaExecHandleBuilder.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getSystemProperties() {
        return javaExecHandleBuilder.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    public void setSystemProperties(Map<String, ?> properties) {
        javaExecHandleBuilder.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec systemProperties(Map<String, ?> properties) {
        javaExecHandleBuilder.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec systemProperty(String name, Object value) {
        javaExecHandleBuilder.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @InputFiles
    public FileCollection getBootstrapClasspath() {
        return javaExecHandleBuilder.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public void setBootstrapClasspath(FileCollection classpath) {
        javaExecHandleBuilder.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec bootstrapClasspath(Object... classpath) {
        javaExecHandleBuilder.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getMinHeapSize() {
        return javaExecHandleBuilder.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMinHeapSize(String heapSize) {
        javaExecHandleBuilder.setMinHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public String getDefaultCharacterEncoding() {
        return javaExecHandleBuilder.getDefaultCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        javaExecHandleBuilder.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    /**
     * {@inheritDoc}
     */
    public String getMaxHeapSize() {
        return javaExecHandleBuilder.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxHeapSize(String heapSize) {
        javaExecHandleBuilder.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public boolean getEnableAssertions() {
        return javaExecHandleBuilder.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnableAssertions(boolean enabled) {
        javaExecHandleBuilder.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public boolean getDebug() {
        return javaExecHandleBuilder.getDebug();
    }

    /**
     * {@inheritDoc}
     */
    @Option(option = "debug-jvm", description = "Enable debugging for the process. The process is started suspended and listening on port 5005. [INCUBATING]")
    public void setDebug(boolean enabled) {
        javaExecHandleBuilder.setDebug(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public String getMain() {
        return javaExecHandleBuilder.getMain();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec setMain(String mainClassName) {
        javaExecHandleBuilder.setMain(mainClassName);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getArgs() {
        return javaExecHandleBuilder.getArgs();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec setArgs(Iterable<?> applicationArgs) {
        javaExecHandleBuilder.setArgs(applicationArgs);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec args(Object... args) {
        javaExecHandleBuilder.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExecSpec args(Iterable<?> args) {
        javaExecHandleBuilder.args(args);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec setClasspath(FileCollection classpath) {
        javaExecHandleBuilder.setClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec classpath(Object... paths) {
        javaExecHandleBuilder.classpath(paths);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @InputFiles
    public FileCollection getClasspath() {
        return javaExecHandleBuilder.getClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec copyTo(JavaForkOptions options) {
        javaExecHandleBuilder.copyTo(options);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getExecutable() {
        return javaExecHandleBuilder.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(Object executable) {
        javaExecHandleBuilder.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec executable(Object executable) {
        javaExecHandleBuilder.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public File getWorkingDir() {
        return javaExecHandleBuilder.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(Object dir) {
        javaExecHandleBuilder.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec workingDir(Object dir) {
        javaExecHandleBuilder.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getEnvironment() {
        return javaExecHandleBuilder.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnvironment(Map<String, ?> environmentVariables) {
        javaExecHandleBuilder.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec environment(String name, Object value) {
        javaExecHandleBuilder.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec environment(Map<String, ?> environmentVariables) {
        javaExecHandleBuilder.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec copyTo(ProcessForkOptions target) {
        javaExecHandleBuilder.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec setStandardInput(InputStream inputStream) {
        javaExecHandleBuilder.setStandardInput(inputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getStandardInput() {
        return javaExecHandleBuilder.getStandardInput();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec setStandardOutput(OutputStream outputStream) {
        javaExecHandleBuilder.setStandardOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public OutputStream getStandardOutput() {
        return javaExecHandleBuilder.getStandardOutput();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExec setErrorOutput(OutputStream outputStream) {
        javaExecHandleBuilder.setErrorOutput(outputStream);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public OutputStream getErrorOutput() {
        return javaExecHandleBuilder.getErrorOutput();
    }

    /**
     * {@inheritDoc}
     */
    public JavaExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        javaExecHandleBuilder.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isIgnoreExitValue() {
        return javaExecHandleBuilder.isIgnoreExitValue();
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getCommandLine() {
        return javaExecHandleBuilder.getCommandLine();
    }
}
