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
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.TestClassProcessorFactory;
import org.gradle.api.testing.detection.DefaultTestClassScannerFactory;
import org.gradle.api.testing.detection.TestClassScanner;
import org.gradle.api.testing.detection.TestClassScannerFactory;
import org.gradle.api.testing.execution.RestartEveryNTestClassProcessor;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.GUtil;

/**
 * A task for executing JUnit (3.8.x or 4.x) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class AntTest extends AbstractTestTask {
    private TestClassScannerFactory testClassScannerFactory;

    public AntTest() {
        this.testClassScannerFactory = new DefaultTestClassScannerFactory();
    }

    void setTestClassScannerFactory(TestClassScannerFactory testClassScannerFactory) {
        this.testClassScannerFactory = testClassScannerFactory;
    }

    public void executeTests() {
        final TestFrameworkInstance testFrameworkInstance = getTestFramework();
        TestClassProcessorFactory processorFactory = testFrameworkInstance.getProcessorFactory();

        TestClassProcessor processor;
        if (getForkEvery() != null) {
            processor = new RestartEveryNTestClassProcessor(processorFactory, getForkEvery());
        } else {
            processor = processorFactory.create();
        }
        TestClassScanner testClassScanner = testClassScannerFactory.createTestClassScanner(this, processor);

        testClassScanner.run();
        testFrameworkInstance.report();

        if (!isIgnoreFailures() && GUtil.isTrue(getProject().getAnt().getProject().getProperty(
                FAILURES_OR_ERRORS_PROPERTY))) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }
}
