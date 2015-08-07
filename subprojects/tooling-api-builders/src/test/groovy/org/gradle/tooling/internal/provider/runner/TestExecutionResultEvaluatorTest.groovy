/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.api.tasks.testing.TestResult
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequestVersion2
import org.gradle.tooling.internal.protocol.test.InternalTestMethod
import org.gradle.tooling.internal.provider.events.DefaultTestDescriptor
import spock.lang.Specification

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators

class TestExecutionResultEvaluatorTest extends Specification {
    def "evaluate throws exception if no results tracked"() {
        given:
        def testExecutionRequest = Mock(InternalTestExecutionRequestVersion2)
        TestExecutionResultEvaluator evaluator = new TestExecutionResultEvaluator(testExecutionRequest)

        def testDescriptor = Mock(TestDescriptor)
        def defaultTestDescriptor = Mock(DefaultTestDescriptor)
        1 * defaultTestDescriptor.getDisplayName() >> "Some Test Descriptor"
        1 * defaultTestDescriptor.getTaskPath() >> ":someTestTask"

        def testResult = Mock(TestResult)
        def internalTestMethod = Mock(InternalTestMethod)
        1 * internalTestMethod.getDescription() >> "Test Method org.acme.SomeOtherTest#someTestMethod()"

        when:
        evaluator.afterSuite(testDescriptor, testResult)
        evaluator.evaluate();
        then:
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.message) == """No matching tests found in any candidate test task.
    Requested Tests:
        Some Test Descriptor (Task: ':someTestTask')
        Test class acme.SomeTestClass
        Test method Test Method org.acme.SomeOtherTest#someTestMethod()"""

        and:
        1 * testExecutionRequest.getTestExecutionDescriptors() >> [defaultTestDescriptor]
        1 * testExecutionRequest.getTestClassNames() >> ["acme.SomeTestClass"]
        1 * testExecutionRequest.getTestMethods() >> [internalTestMethod]
    }

    def "evaluate throws exception if test failed"() {
        given:
        def testExecutionRequest = Mock(InternalTestExecutionRequestVersion2)
        TestExecutionResultEvaluator evaluator = new TestExecutionResultEvaluator(testExecutionRequest)
        def testDescriptor = Mock(TestDescriptor)
        def testResult = Mock(TestResult)
        1 * testResult.getTestCount() >> 1
        1 * testResult.getFailedTestCount() >> 1
        when:
        evaluator.afterSuite(testDescriptor, testResult)
        evaluator.evaluate()
        then:
        def e = thrown(TestExecutionException)
        e.message == "Test(s) failed!"
    }
}
