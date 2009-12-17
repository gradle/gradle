/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.testing.detection.SetBuildingTestClassProcessor;
import org.gradle.api.testing.detection.TestClassScanner;
import org.gradle.api.testing.detection.TestClassScannerFactory;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * A task for executing JUnit (3.8.x or 4) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class AntTest extends AbstractTestTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(AntTest.class);

    private TestClassScannerFactory testClassScannerFactory;
    private SetBuildingTestClassProcessor testClassProcessor;

    public AntTest() {
        super();
        testClassScannerFactory = new DefaultTestClassScannerFactory();
        testClassProcessor = new SetBuildingTestClassProcessor();
    }

    public void executeTests() {
        final TestFrameworkInstance testFrameworkInstance = getTestFramework();

        final Set<String> includes = getIncludes();
        final Set<String> excludes = getExcludes();

        final TestClassScanner testClassScanner = testClassScannerFactory.createTestClassScanner(this,
                testClassProcessor);

        testClassScanner.executeScan();

        final Set<String> testClassNames = testClassProcessor.getTestClassNames();

        Collection<String> toUseIncludes = null;
        Collection<String> toUseExcludes = null;
        if (testClassNames.isEmpty()) {
            toUseIncludes = includes;
            toUseExcludes = excludes;
        } else {
            toUseIncludes = testClassNames;
            toUseExcludes = new ArrayList<String>();
        }

        if (!(toUseIncludes.isEmpty() && toUseExcludes.isEmpty())) {
            testFrameworkInstance.execute(getProject(), this, toUseIncludes, toUseExcludes);
        } else // when there are no includes/excludes -> don't execute test framework
        {
            LOGGER.debug("skipping test execution, because no tests were found");
        }
        // TestNG execution fails when there are no tests
        // JUnit execution doesn't fail when there are no tests

        if (testReport) {
            testFrameworkInstance.report(getProject(), this);
        }

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

    /**
     * only for test purposes
     */
    void setTestClassProcessor(SetBuildingTestClassProcessor testClassProcessor) {
        this.testClassProcessor = testClassProcessor;
    }
}
