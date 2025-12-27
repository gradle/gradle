/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.nonclassbased

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import testengines.TestEnginesFixture

/**
 * Abstract base class for tests that exercise and demonstrate incorrect Non-Class-Based Testing setups.
 */
abstract class AbstractNonClassBasedTestingIntegrationTest extends AbstractIntegrationSpec implements TestEnginesFixture, VerifiesGenericTestReportResults {
    public static final DEFAULT_DEFINITIONS_LOCATION = "src/test/definitions"

    @Override
    TestFramework getTestFramework() {
        return TestFramework.CUSTOM
    }

    protected void sourcesPresentAndNoTestsFound() {
        failureCauseContains("There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.")
    }

    protected void nonClassBasedTestsExecuted(boolean exactlyTheseTests = true) {
        if (exactlyTheseTests) {
            resultsFor().assertTestPathsExecuted(":SomeTestSpec.rbt - foo", ":SomeTestSpec.rbt - bar", ":SomeOtherTestSpec.rbt - other")
        } else {
            resultsFor().assertAtLeastTestPathsExecuted(":SomeTestSpec.rbt - foo", ":SomeTestSpec.rbt - bar", ":SomeOtherTestSpec.rbt - other")
        }
    }

    protected void classBasedTestsExecuted(boolean exactlyTheseTests = true) {
        if (exactlyTheseTests) {
            resultsFor().assertTestPathsExecuted(":SomeTest:testMethod()")
        } else {
            resultsFor().assertAtLeastTestPathsExecuted(":SomeTest:testMethod()")
        }
    }

    protected void writeTestClasses() {
        file("src/test/java/SomeTest.java") << """
            import org.junit.jupiter.api.Test;

            public class SomeTest {
                @Test
                public void testMethod() {
                    System.out.println("Tested!");
                }
            }
        """
    }

    protected void writeTestDefinitions(String path = "src/test/definitions") {
        file("$path/SomeTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="foo" />
                <test name="bar" />
            </tests>
        """
        file("$path/sub/SomeOtherTestSpec.rbt") << """<?xml version="1.0" encoding="UTF-8" ?>
            <tests>
                <test name="other" />
            </tests>
        """
    }
}
