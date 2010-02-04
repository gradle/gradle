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
import org.gradle.api.JavaVersion;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.testng.AntTestNGExecute;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.fabric.AbstractTestFrameworkInstance;

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
        return new TestClassProcessorFactory() {
            public TestClassProcessor create() {
                options.setTestResources(testTask.getTestSrcDirs());
                return new AntTestNGExecute(testTask.getTestClassesDir(), testTask.getClasspath(),
                        testTask.getTestResultsDir(), testTask.getTestReportDir(), options,
                        testTask.getProject().getAnt(), testTask.getTestListenerBroadcaster());
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
}
