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

package org.gradle.api.tasks.testing;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestSummaryListener;
import org.gradle.api.internal.tasks.util.DefaultJavaForkOptions;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.tasks.util.ProcessForkOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.detection.DefaultTestClassScannerFactory;
import org.gradle.api.testing.detection.TestClassScannerFactory;
import org.gradle.api.testing.execution.RestartEveryNTestClassProcessor;
import org.gradle.api.testing.execution.fork.ForkingTestClassProcessor;
import org.gradle.api.testing.execution.fork.WorkerTestClassProcessorFactory;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.process.WorkerProcessFactory;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A task for executing JUnit (3.8.x or 4.x) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class AntTest extends AbstractTestTask implements JavaForkOptions {
    private TestClassScannerFactory testClassScannerFactory;
    private final DefaultJavaForkOptions options;

    public AntTest() {
        this.testClassScannerFactory = new DefaultTestClassScannerFactory();
        options = new DefaultJavaForkOptions(getServices().get(FileResolver.class));
        options.setEnableAssertions(true);
    }

    void setTestClassScannerFactory(TestClassScannerFactory testClassScannerFactory) {
        this.testClassScannerFactory = testClassScannerFactory;
    }

    /**
     * {@inheritDoc}
     */
    public File getWorkingDir() {
        return options.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(Object dir) {
        options.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public AntTest workingDir(Object dir) {
        options.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getExecutable() {
        return options.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public AntTest executable(Object executable) {
        options.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(Object executable) {
        options.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getSystemProperties() {
        return options.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    public void setSystemProperties(Map<String, ?> properties) {
        options.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    public AntTest systemProperties(Map<String, ?> properties) {
        options.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AntTest systemProperty(String name, Object value) {
        options.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public FileCollection getBootstrapClasspath() {
        return options.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public void setBootstrapClasspath(Iterable<?> classpath) {
        options.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    public AntTest bootstrapClasspath(Iterable<?> classpath) {
        options.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AntTest bootstrapClasspath(Object... classpath) {
        options.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getMaxHeapSize() {
        return options.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxHeapSize(String heapSize) {
        options.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getJvmArgs() {
        return options.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setJvmArgs(Iterable<?> arguments) {
        options.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public AntTest jvmArgs(Iterable<?> arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AntTest jvmArgs(Object... arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public boolean getEnableAssertions() {
        return options.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnableAssertions(boolean enabled) {
        options.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getAllJvmArgs() {
        return options.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setAllJvmArgs(Iterable<?> arguments) {
        options.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getEnvironment() {
        return options.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    public AntTest environment(Map<String, ?> environmentVariables) {
        options.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AntTest environment(String name, Object value) {
        options.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void setEnvironment(Map<String, ?> environmentVariables) {
        options.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    public AntTest copyTo(ProcessForkOptions target) {
        options.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public AntTest copyTo(JavaForkOptions target) {
        options.copyTo(target);
        return this;
    }

    public void executeTests() {
        final WorkerProcessFactory workerFactory = getServices().get(WorkerProcessFactory.class);

        final TestFrameworkInstance testFrameworkInstance = getTestFramework();
        final WorkerTestClassProcessorFactory testInstanceFactory = testFrameworkInstance.getProcessorFactory();
        TestClassProcessorFactory processorFactory = new TestClassProcessorFactory() {
            public TestClassProcessor create() {
                return new ForkingTestClassProcessor(workerFactory, testInstanceFactory, options, getClasspath(), testFrameworkInstance.getWorkerConfigurationAction());
            }
        };

        TestClassProcessor processor;
        if (getForkEvery() != null) {
            processor = new RestartEveryNTestClassProcessor(processorFactory, getForkEvery());
        } else {
            processor = processorFactory.create();
        }

        TestSummaryListener listener = new TestSummaryListener(LoggerFactory.getLogger(AntTest.class));
        addTestListener(listener);

        TestResultProcessor resultProcessor = new TestListenerAdapter(getTestListenerBroadcaster().getSource());
        Runnable testClassScanner = testClassScannerFactory.createTestClassScanner(this, processor, resultProcessor);
        testClassScanner.run();

        testFrameworkInstance.report();

        if (!isIgnoreFailures() && listener.hadFailures()) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }
}
