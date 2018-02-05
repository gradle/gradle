/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.internal.tasks.testing.results.TestListenerInternal
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class FailFastTestListenerTestInternal extends Specification {
    TestExecuter testExecuter = Mock()
    TestListenerInternal delegate = Mock()
    TestDescriptorInternal testDescriptor = Mock()
    TestResult testResult = Mock()
    TestCompleteEvent completeEvent = Mock()

    @Subject
    FailFastTestListenerInternal unit

    def setup() {
        unit = new FailFastTestListenerInternal(testExecuter, delegate)
    }

    def "started invokes delegate"() {
        TestStartEvent startEvent = Mock()

        when:
        unit.started(testDescriptor, startEvent)

        then:
        1 * delegate.started(testDescriptor, startEvent)
        0 * _
    }

    def "output invokes delegate"() {
        TestOutputEvent event = Mock()

        when:
        unit.output(testDescriptor, event)

        then:
        1 * delegate.output(testDescriptor, event)
        0 * _
    }

    def "completed calls stopNow on failure"() {
        when:
        testResult.getResultType() >> TestResult.ResultType.FAILURE
        unit.completed(testDescriptor, testResult, completeEvent)

        then:
        1 * testExecuter.stopNow()
    }

    @Unroll
    def "completed invokes delegate with result #result"() {
        when:
        testResult.getResultType() >> result
        unit.completed(testDescriptor, testResult, completeEvent)

        then:
        1 * delegate.completed(testDescriptor, testResult, completeEvent)

        where:
        result << TestResult.ResultType.values()
    }

    @Unroll
    def "after failure completed indicates failure on composite for result #result"() {
        TestResult failedResult = Mock()

        when:
        unit.completed(testDescriptor, failedResult, completeEvent)

        then:
        1 * failedResult.getResultType() >> TestResult.ResultType.FAILURE

        when:
        testResult.getResultType() >> result
        unit.completed(testDescriptor, testResult, completeEvent)

        then:
        1 * testDescriptor.isComposite() >> true
        1 * delegate.completed(testDescriptor, { it.getResultType() == TestResult.ResultType.FAILURE }, completeEvent)

        where:
        result << TestResult.ResultType.values()
    }

    @Unroll
    def "after failure completed indicates skipped on non-composite for result #result"() {
        TestResult failedResult = Mock()

        when:
        unit.completed(testDescriptor, failedResult, completeEvent)

        then:
        1 * failedResult.getResultType() >> TestResult.ResultType.FAILURE

        when:
        testResult.getResultType() >> result
        unit.completed(testDescriptor, testResult, completeEvent)

        then:
        1 * testDescriptor.isComposite() >> false
        1 * delegate.completed(testDescriptor, { it.getResultType() == TestResult.ResultType.SKIPPED }, completeEvent)

        where:
        result << TestResult.ResultType.values()
    }
}
