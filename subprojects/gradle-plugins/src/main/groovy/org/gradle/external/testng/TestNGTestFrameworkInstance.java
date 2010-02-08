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
package org.gradle.external.testng;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestClassProcessor;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.fabric.AbstractTestFrameworkInstance;
import org.gradle.process.WorkerProcessBuilder;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFrameworkInstance extends AbstractTestFrameworkInstance {

    private TestNGOptions options;
    private TestNGDetector detector;

    protected TestNGTestFrameworkInstance(AbstractTestTask testTask, TestNGTestFramework testFramework) {
        super(testTask, testFramework);
    }

    public void initialize() {
        options = new TestNGOptions((TestNGTestFramework) testFramework, testTask.getProject().getProjectDir());
        options.setAnnotationsOnSourceCompatibility(JavaVersion.toVersion(testTask.getProject().property(
                "sourceCompatibility")));
        detector = new TestNGDetector(testTask.getTestClassesDir(), testTask.getClasspath());
    }

    public TestClassProcessorFactory getProcessorFactory() {
        options.setTestResources(testTask.getTestSrcDirs());
        return new TestClassProcessorFactoryImpl(testTask.getTestReportDir());
    }

    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("org.testng");
            }
        };
    }

    public void report() {
        // TODO currently reports are always generated because the antTestNGExecute task uses the
        // default listeners and these generate reports by default.
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

    public void applyForkArguments(JavaForkOptions javaForkOptions) {
        if (StringUtils.isNotEmpty(options.getJvm())) {
            javaForkOptions.executable(options.getJvm());
        }

        final List<String> jvmArgs = options.getJvmArgs();
        if (jvmArgs != null && !jvmArgs.isEmpty()) {
            javaForkOptions.jvmArgs(jvmArgs);
        }

        final Map<String, String> environment = options.getEnvironment();
        if (environment != null && !environment.isEmpty()) {
            javaForkOptions.setEnvironment(environment);
        }
    }

    private static class TestClassProcessorFactoryImpl implements TestClassProcessorFactory, Serializable {
        private final File testReportDir;

        public TestClassProcessorFactoryImpl(File testReportDir) {
            this.testReportDir = testReportDir;
        }

        public TestClassProcessor create() {
            return new TestNGTestClassProcessor(testReportDir);
        }
    }
}
