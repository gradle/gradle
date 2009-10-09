/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.*;
import org.gradle.api.testing.detection.SetBuildingTestClassReceiver;
import org.gradle.api.testing.detection.TestClassScanner;
import org.gradle.api.testing.detection.TestClassReceiver;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * A task for executing JUnit (3.8.x or 4) or TestNG tests.
 *
 * @author Hans Dockter
 */
public class AntTest extends AbstractTestTask {
    private static final Logger logger = LoggerFactory.getLogger(AntTest.class);

    private TestClassReceiver testClassReceiver;

    public AntTest() {
        super();
        testClassReceiver = new SetBuildingTestClassReceiver();
    }

    public void executeTests() {
        final File testClassesDir = getTestClassesDir();

        final TestFrameworkInstance testInstance = getTestFramework();

        testInstance.prepare(getProject(), this, testClassReceiver);

        final Set<String> includes = getIncludes();
        final Set<String> excludes = getExcludes();

        final TestClassScanner testClassScanner = new TestClassScanner(
                testClassesDir,
                includes, excludes,
                testFrameworkInstance,
                scanForTestClasses
        );

        testClassScanner.getTestClassNames();

        final Set<String> testClassNames = ((SetBuildingTestClassReceiver)testClassReceiver).getTestClassNames();

        Collection<String> toUseIncludes = null;
        Collection<String> toUseExcludes = null;
        if (testClassNames.isEmpty()) {
            toUseIncludes = includes;
            toUseExcludes = excludes;
        } else {
            toUseIncludes = testClassNames;
            toUseExcludes = new ArrayList<String>();
        }

        GFileUtils.createDirectoriesWhenNotExistent(getTestResultsDir());// needed for JUnit reporting

        if (!(toUseIncludes.isEmpty() && toUseExcludes.isEmpty()))
            testInstance.execute(getProject(), this, toUseIncludes, toUseExcludes);
        else // when there are no includes/excludes -> don't execute test framework
            logger.debug("skipping test execution, because no tests were found");
        // TestNG execution fails when there are no tests
        // JUnit execution doesn't fail when there are no tests

        if (testReport) {
            testInstance.report(getProject(), this);
        }

        if (stopAtFailuresOrErrors && GUtil.isTrue(getProject().getAnt().getProject().getProperty(FAILURES_OR_ERRORS_PROPERTY))) {
            throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
        }
    }

    /**
     * only for test purposes
     *
     * @param testClassReceiver
     */
    public void setTestClassReceiver(TestClassReceiver testClassReceiver) {
        this.testClassReceiver = testClassReceiver;
    }
}
