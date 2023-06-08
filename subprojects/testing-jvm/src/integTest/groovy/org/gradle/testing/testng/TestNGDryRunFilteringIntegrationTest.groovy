/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.testing.testng

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestOutcome
import org.gradle.testing.DryRunFilteringTest
import org.gradle.testing.fixture.TestNGCoverage

@TargetCoverage({ TestNGCoverage.SUPPORTS_DRY_RUN })
class TestNGDryRunFilteringIntegrationTest extends AbstractTestNGFilteringIntegrationTest implements DryRunFilteringTest {
    @Override
    TestOutcome getPassedTestOutcome() {
        return TestOutcome.PASSED
    }

    @Override
    TestOutcome getFailedTestOutcome() {
        return TestOutcome.PASSED
    }

    def "dry-run property not preserved across invocations"() {
        given:
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test {
              ${configureTestFramework}
            }
        """

        file("src/test/java/FailingTest.java") << """
            ${testFrameworkImports}
            public class FailingTest {
                @Test public void failing() {
                    throw new RuntimeException("Boo!");
                }
            }
        """

        def testResult = new DefaultTestExecutionResult(testDirectory)

        when:
        succeeds("test", "--test-dry-run")

        then:
        testResult.testClass("FailingTest").assertTestOutcomes(getFailedTestOutcome(), "failing")

        when:
        fails("test")

        then:
        testResult.testClass("FailingTest").assertTestFailedIgnoreMessages("failing")
    }
}
