/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestNGExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.gradle.testing.fixture.TestNGCoverage.NEWEST

class TestNGFailurePolicyIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule public TestResources resources = new TestResources(testDirectoryProvider)

    TestClassExecutionResult getTestResults() {
        new TestNGExecutionResult(testDirectory).testClass("org.gradle.failurepolicy.TestWithFailureInConfigMethod")
    }

    void usingTestNG(String version) {
        buildFile << """
            dependencies { testCompile "org.testng:testng:${version}" }
        """
    }

    def "skips tests after a config method failure by default"() {
        when:
        usingTestNG(NEWEST)

        then:
        fails "test"

        and:
        testResults.assertConfigMethodFailed("fail")
        testResults.assertTestSkipped("someTest")
    }

    def "can be configured to continue executing tests after a config method failure"() {
        when:
        usingTestNG(NEWEST)
        buildFile << """
            test.options {
                configFailurePolicy "continue"
            }
        """

        then:
        fails "test"

        and:
        testResults.assertConfigMethodFailed("fail")
        testResults.assertTestPassed("someTest")
    }

    def "informative error is shown when trying to use config failure policy and a version that does not support it"() {
        when:
        usingTestNG("5.12.1")
        buildFile << """
            test.options {
                configFailurePolicy "continue"
            }
        """

        then:
        fails "test"

        and:
        failure.assertHasCause("The version of TestNG used does not support setting config failure policy to 'continue'.")
    }
}
