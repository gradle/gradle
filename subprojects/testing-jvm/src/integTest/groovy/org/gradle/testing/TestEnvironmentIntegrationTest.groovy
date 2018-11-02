/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.gradle.util.Matchers
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class TestEnvironmentIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    def canRunTestsWithCustomSystemClassLoader() {
        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySystemClassLoaderIsUsed')
    }

    def canRunTestsReferencingSlf4jWithCustomSystemClassLoader() {
        when:
        run 'test'

        then:
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.assertTestClassesExecuted('org.gradle.TestUsingSlf4j')
        with(testResults.testClass('org.gradle.TestUsingSlf4j')) {
            assertTestPassed('mySystemClassLoaderIsUsed')
            assertStderr(Matchers.containsText("ERROR via slf4j"))
            assertStderr(Matchers.containsText("WARN via slf4j"))
            assertStderr(Matchers.containsText("INFO via slf4j"))
        }
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def canRunTestsReferencingSlf4jWithModularJava() {
        when:
        run 'test'

        then:
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.assertTestClassesExecuted('org.gradle.example.TestUsingSlf4j')
        with(testResults.testClass('org.gradle.example.TestUsingSlf4j')) {
            assertTestPassed('testModular')
            assertStderr(Matchers.containsText("ERROR via slf4j"))
            assertStderr(Matchers.containsText("WARN via slf4j"))
            assertStderr(Matchers.containsText("INFO via slf4j"))
        }
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER) //hangs on Java9
    def canRunTestsWithCustomSystemClassLoaderAndJavaAgent() {
        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySystemClassLoaderIsUsed')
    }

    def canRunTestsWithCustomSecurityManager() {
        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySecurityManagerIsUsed')
    }

    @Requires(TestPrecondition.JDK7_OR_EARLIER)
    def canRunTestsWithJMockitLoadedWithJavaAgent() {
        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JMockitTest')
        result.testClass('org.gradle.JMockitTest').assertTestPassed('testOk')
    }
}
