/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging

import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.DefaultTestFailureDetails
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import spock.lang.Specification

class SimpleTestEventLoggerTest extends Specification {
    def "started and output do nothing"() {
        def textOutputFactory = Mock(StyledTextOutputFactory)
        def logger = new SimpleTestEventLogger(textOutputFactory)

        when:
        logger.started(Mock(TestDescriptorInternal), Mock(TestStartEvent))
        logger.output(Mock(TestDescriptorInternal), Mock(TestOutputEvent))
        then:
        0 * _
    }

    def "renders failures for simple test"() {
        def textOutputFactory = new TestStyledTextOutputFactory()
        def logger = new SimpleTestEventLogger(textOutputFactory)

        def descriptor = new DefaultTestDescriptor(0, "Class", "method", "Class", "method()")
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 0, 0, 0, [new DefaultTestFailure(null, new DefaultTestFailureDetails("message", "Exception", "stack", false, false, null, null, null, null), [])])
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        logger.completed(descriptor, result, complete)
        then:
        textOutputFactory.category == SimpleTestEventLogger.canonicalName
        textOutputFactory.logLevel == null
        textOutputFactory.output == """
method() {failure}FAILED{normal}
    {identifier}Exception{normal}: message
"""
    }

    def "does not render skipped"() {
        def textOutputFactory = new TestStyledTextOutputFactory()
        def logger = new SimpleTestEventLogger(textOutputFactory)

        def descriptor = new DefaultTestDescriptor(0, "Class", "method", "Class", "method()")
        def result = new DefaultTestResult(TestResult.ResultType.SKIPPED, 0, 0, 0, 0, 0, [])
        def complete = new TestCompleteEvent(0, TestResult.ResultType.SKIPPED)

        when:
        logger.completed(descriptor, result, complete)
        then:
        textOutputFactory.output == ""
    }

    def "does not render success"() {
        def textOutputFactory = new TestStyledTextOutputFactory()
        def logger = new SimpleTestEventLogger(textOutputFactory)

        def descriptor = new DefaultTestDescriptor(0, "Class", "method", "Class", "method()")
        def result = new DefaultTestResult(TestResult.ResultType.SUCCESS, 0, 0, 0, 0, 0, [])
        def complete = new TestCompleteEvent(0, TestResult.ResultType.SUCCESS)

        when:
        logger.completed(descriptor, result, complete)
        then:
        textOutputFactory.output == ""
    }
}
