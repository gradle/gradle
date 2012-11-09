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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.junit.JULRedirector;
import org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport;
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFramework implements TestFramework {

    private final static Logger LOG = Logging.getLogger(TestNGTestFramework.class);

    private TestNGOptions options;
    private TestNGDetector detector;
    final Test testTask;
    final TestReporter reporter;

    public TestNGTestFramework(Test testTask) {
        this(testTask, new DefaultTestReport());
    }

    public TestNGTestFramework(Test testTask, TestReporter reporter) {
        this.testTask = testTask;
        this.reporter = reporter;
        options = new TestNGOptions(testTask.getProject().getProjectDir());
        options.setAnnotationsOnSourceCompatibility(JavaVersion.toVersion(testTask.getProject().property("sourceCompatibility")));
        detector = new TestNGDetector(new ClassFileExtractionManager(testTask.getTemporaryDirFactory()));
    }

    public WorkerTestClassProcessorFactory getProcessorFactory() {
        options.setTestResources(testTask.getTestSrcDirs());
        List<File> suiteFiles = options.getSuites(testTask.getTemporaryDir());
        return new TestClassProcessorFactoryImpl(testTask.getTestReportDir(), options, suiteFiles,
                testTask.getTestResultsDir(), testTask.isTestReport());
    }

    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("org.testng");
            }
        };
    }

    public void report() {
        if (!testTask.isTestReport()) {
            LOG.info("Test report disabled, omitting generation of the html test report.");
            return;
        }
        // TODO SF make the logging consistent in frameworks, sanitize JUnit coverage (it's jmock)
        LOG.info("Generating html test report...");
        reporter.setTestReportDir(testTask.getTestReportDir());
        reporter.setTestResultsDir(testTask.getTestResultsDir());
        reporter.generateReport();
    }

    public TestNGOptions getOptions() {
        return options;
    }

    void setOptions(TestNGOptions options) {
        this.options = options;
    }

    public TestNGDetector getDetector() {
        return detector;
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final File testReportDir;
        private final TestNGOptions options;
        private final List<File> suiteFiles;
        private final File testResultsDir;
        private final boolean testReportOn;

        public TestClassProcessorFactoryImpl(File testReportDir, TestNGOptions options, List<File> suiteFiles,
                                             File testResultsDir, boolean testReportOn) {
            this.testReportDir = testReportDir;
            this.options = options;
            this.suiteFiles = suiteFiles;
            this.testResultsDir = testResultsDir;
            this.testReportOn = testReportOn;
        }

        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new TestNGTestClassProcessor(testReportDir, options, suiteFiles,
                    serviceRegistry.get(IdGenerator.class), new JULRedirector(),
                    testResultsDir, testReportOn);
        }
    }
}
