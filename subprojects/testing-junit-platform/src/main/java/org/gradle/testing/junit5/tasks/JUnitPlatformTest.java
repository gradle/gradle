/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.testing.junit5.tasks;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.testing.junit5.JUnitPlatformOptions;
import org.gradle.testing.junit5.internal.JUnitPlatformTestExecutionSpec;
import org.gradle.testing.junit5.internal.JUnitPlatformTestExecutor;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;

public class JUnitPlatformTest extends AbstractTestTask implements JavaForkOptions {
    private final WorkerExecutor workerExecutor;
    private final JavaForkOptions forkOptions;
    private final JUnitPlatformOptions options;
    private FileCollection classpath;

    @Inject
    public JUnitPlatformTest(WorkerExecutor workerExecutor, FileResolver fileResolver) {
        this.workerExecutor = workerExecutor;
        this.forkOptions = new DefaultJavaForkOptions(fileResolver);
        this.options = new JUnitPlatformOptions();
    }

    @Override
    protected JUnitPlatformTestExecutor createTestExecuter() {
        return new JUnitPlatformTestExecutor(workerExecutor, forkOptions);
    }

    @Override
    protected JUnitPlatformTestExecutionSpec createTestExecutionSpec() {
        return new JUnitPlatformTestExecutionSpec(options, classpath);
    }

    @Nested
    public JUnitPlatformOptions getOptions() {
        return options;
    }

    public void options(Action<JUnitPlatformOptions> configureAction) {
        configureAction.execute(options);
    }

    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @Override
    @Input
    public Map<String, Object> getSystemProperties() {
        return forkOptions.getSystemProperties();
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        forkOptions.setSystemProperties(properties);
    }

    @Override
    public JavaForkOptions systemProperties(Map<String, ?> properties) {
        return forkOptions.systemProperties(properties);
    }

    @Override
    public JavaForkOptions systemProperty(String name, Object value) {
        return forkOptions.systemProperty(name, value);
    }

    @Override
    @Optional
    @Input
    public String getDefaultCharacterEncoding() {
        return forkOptions.getDefaultCharacterEncoding();
    }

    @Override
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        forkOptions.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Override
    @Optional
    @Input
    public String getMinHeapSize() {
        return forkOptions.getMinHeapSize();
    }

    @Override
    public void setMinHeapSize(String heapSize) {
        forkOptions.setMinHeapSize(heapSize);
    }

    @Override
    @Optional
    @Input
    public String getMaxHeapSize() {
        return forkOptions.getMaxHeapSize();
    }

    @Override
    public void setMaxHeapSize(String heapSize) {
        forkOptions.setMaxHeapSize(heapSize);
    }

    @Override
    @Optional
    @Input
    public List<String> getJvmArgs() {
        return forkOptions.getJvmArgs();
    }

    @Override
    public void setJvmArgs(List<String> arguments) {
        forkOptions.setJvmArgs(arguments);
    }

    @Override
    public void setJvmArgs(Iterable<?> arguments) {
        forkOptions.setJvmArgs(arguments);
    }

    @Override
    public JavaForkOptions jvmArgs(Iterable<?> arguments) {
        return forkOptions.jvmArgs(arguments);
    }

    @Override
    public JavaForkOptions jvmArgs(Object... arguments) {
        return forkOptions.jvmArgs(arguments);
    }

    @Override
    @Classpath
    public FileCollection getBootstrapClasspath() {
        return forkOptions.getBootstrapClasspath();
    }

    @Override
    public void setBootstrapClasspath(FileCollection classpath) {
        forkOptions.setBootstrapClasspath(classpath);
    }

    @Override
    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        return forkOptions.bootstrapClasspath(classpath);
    }

    @Override
    @Input
    public boolean getEnableAssertions() {
        return forkOptions.getEnableAssertions();
    }

    @Override
    public void setEnableAssertions(boolean enabled) {
        forkOptions.setEnableAssertions(enabled);
    }

    @Override
    @Input
    public boolean getDebug() {
        return forkOptions.getDebug();
    }

    @Override
    public void setDebug(boolean enabled) {
        forkOptions.setDebug(enabled);
    }

    @Override
    @Internal
    public List<String> getAllJvmArgs() {
        return forkOptions.getAllJvmArgs();
    }

    @Override
    public void setAllJvmArgs(List<String> arguments) {
        forkOptions.setAllJvmArgs(arguments);
    }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        forkOptions.setAllJvmArgs(arguments);
    }

    @Override
    public JavaForkOptions copyTo(JavaForkOptions options) {
        return forkOptions.copyTo(options);
    }

    @Override
    @Internal
    public String getExecutable() {
        return forkOptions.getExecutable();
    }

    @Override
    public void setExecutable(String executable) {
        forkOptions.setExecutable(executable);
    }

    @Override
    public void setExecutable(Object executable) {
        forkOptions.setExecutable(executable);
    }

    @Override
    public ProcessForkOptions executable(Object executable) {
        return forkOptions.executable(executable);
    }

    @Override
    @Internal
    public File getWorkingDir() {
        return forkOptions.getWorkingDir();
    }

    @Override
    public void setWorkingDir(File dir) {
        forkOptions.setWorkingDir(dir);
    }

    @Override
    public void setWorkingDir(Object dir) {
        forkOptions.setWorkingDir(dir);
    }

    @Override
    public ProcessForkOptions workingDir(Object dir) {
        return forkOptions.workingDir(dir);
    }

    @Override
    @Internal
    public Map<String, Object> getEnvironment() {
        return forkOptions.getEnvironment();
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        forkOptions.setEnvironment(environmentVariables);
    }

    @Override
    public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
        return forkOptions.environment(environmentVariables);
    }

    @Override
    public ProcessForkOptions environment(String name, Object value) {
        return forkOptions.environment(name, value);
    }

    @Override
    public ProcessForkOptions copyTo(ProcessForkOptions options) {
        return forkOptions.copyTo(options);
    }
}
