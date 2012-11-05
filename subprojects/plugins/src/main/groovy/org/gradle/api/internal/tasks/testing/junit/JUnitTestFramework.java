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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.ClassFileExtractionManager;
import org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport;
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;
import java.io.Serializable;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestFramework implements TestFramework {
    private TestReporter reporter;
    private JUnitOptions options;
    private JUnitDetector detector;
    private final Test testTask;

    public JUnitTestFramework(Test testTask) {
        this.testTask = testTask;
        reporter = new DefaultTestReport();
        options = new JUnitOptions();
        detector = new JUnitDetector(new ClassFileExtractionManager(testTask.getTemporaryDirFactory()));
    }

    public WorkerTestClassProcessorFactory getProcessorFactory() {
        final File testResultsDir = testTask.getTestResultsDir();
        return new TestClassProcessorFactoryImpl(testResultsDir);
    }

    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("junit.framework");
                workerProcessBuilder.sharedPackages("junit.extensions");
                workerProcessBuilder.sharedPackages("org.junit");
            }
        };
    }

    public void report() {
        if (!testTask.isTestReport()) {
            return;
        }
        reporter.setTestReportDir(testTask.getTestReportDir());
        reporter.setTestResultsDir(testTask.getTestResultsDir());
        reporter.generateReport();
    }

    public JUnitOptions getOptions() {
        return options;
    }

    void setOptions(JUnitOptions options) {
        this.options = options;
    }

    TestReporter getReporter() {
        return reporter;
    }

    void setReporter(TestReporter reporter) {
        this.reporter = reporter;
    }

    public JUnitDetector getDetector() {
        return detector;
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final File testResultsDir;

        public TestClassProcessorFactoryImpl(File testResultsDir) {
            this.testResultsDir = testResultsDir;
        }

        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new JUnitTestClassProcessor(testResultsDir, serviceRegistry.get(IdGenerator.class), serviceRegistry.get(ActorFactory.class), new JULRedirector());
        }
    }
}
