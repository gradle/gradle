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

package org.gradle.external.junit;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.junit.AntJUnitReport;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassProcessor;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.execution.fork.WorkerTestClassProcessorFactory;
import org.gradle.api.testing.fabric.AbstractTestFrameworkInstance;
import org.gradle.process.WorkerProcessBuilder;
import org.gradle.util.IdGenerator;

import java.io.File;
import java.io.Serializable;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFrameworkInstance extends AbstractTestFrameworkInstance {
    private AntJUnitReport antJUnitReport;
    private JUnitOptions options;
    private JUnitDetector detector;

    protected JUnitTestFrameworkInstance(AbstractTestTask testTask, JUnitTestFramework testFramework) {
        super(testTask, testFramework);
    }

    public void initialize() {
        antJUnitReport = new AntJUnitReport();
        options = new JUnitOptions();
        detector = new JUnitDetector(testTask.getTestClassesDir(), testTask.getClasspath());
    }

    public WorkerTestClassProcessorFactory getProcessorFactory() {
        final File testResultsDir = testTask.getTestResultsDir();
        return new TestClassProcessorFactoryImpl(testResultsDir);
    }

    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("junit.framework");
                workerProcessBuilder.sharedPackages("org.junit");
            }
        };
    }

    public void report() {
        if (!testTask.isTestReport()) {
            return;
        }
        antJUnitReport.execute(testTask.getTestResultsDir(), testTask.getTestReportDir(),
                testTask.getProject().getAnt());
    }

    public JUnitOptions getOptions() {
        return options;
    }

    void setOptions(JUnitOptions options) {
        this.options = options;
    }

    AntJUnitReport getAntJUnitReport() {
        return antJUnitReport;
    }

    void setAntJUnitReport(AntJUnitReport antJUnitReport) {
        this.antJUnitReport = antJUnitReport;
    }

    public JUnitDetector getDetector() {
        return detector;
    }

    public void applyForkArguments(JavaForkOptions javaForkOptions) {
        // TODO - implement
        // TODO clone
        // TODO bootstrapClasspath - not sure which bootstrap classpath option to use:
        // TODO one of: -Xbootclasspath or -Xbootclasspath/a or -Xbootclasspath/p
        // TODO -Xbootclasspath/a seems the correct one - to discuss or improve and make it
        // TODO possible to specify which one to use. -> will break ant task compatibility in options.
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final File testResultsDir;

        public TestClassProcessorFactoryImpl(File testResultsDir) {
            this.testResultsDir = testResultsDir;
        }

        public TestClassProcessor create(IdGenerator<?> idGenerator) {
            return new JUnitTestClassProcessor(testResultsDir, idGenerator);
        }
    }
}
