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
import org.gradle.api.testing.detection.DefaultTestClassScannerFactory;
import org.gradle.api.testing.detection.TestClassScanner;
import org.gradle.api.testing.detection.TestClassScannerFactory;
import org.gradle.api.testing.execution.ant.AntTaskBackedTestClassProcessor;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.GUtil;

/**
 * A task for executing JUnit (3.8.x or 4) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class AntTest extends AbstractTestTask {
    private TestClassScannerFactory testClassScannerFactory;

    public AntTest() {
        super();
        testClassScannerFactory = new DefaultTestClassScannerFactory();
    }

    public void executeTests() {
        TestFrameworkInstance testFrameworkInstance = getTestFramework();

        AntTaskBackedTestClassProcessor processor = new AntTaskBackedTestClassProcessor(testFrameworkInstance);
        TestClassScanner testClassScanner = testClassScannerFactory.createTestClassScanner(this, processor);

        testClassScanner.run();

        if (!isIgnoreFailures() && GUtil.isTrue(getProject().getAnt().getProject().getProperty(
                FAILURES_OR_ERRORS_PROPERTY))) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }

    /**
     * only for test purposes
     */
    void setTestClassScannerFactory(TestClassScannerFactory testClassScannerFactory) {
        this.testClassScannerFactory = testClassScannerFactory;
    }

}
