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

package org.gradle.api.internal.tasks.testing.processors

import org.gradle.api.internal.tasks.testing.*
import spock.lang.Specification
import spock.lang.Subject

class CaptureTestOutputTestResultProcessorTest extends Specification {

    TestResultProcessor target = Mock()
    TestOutputRedirector redirector = Mock()
    @Subject processor = new CaptureTestOutputTestResultProcessor(target, redirector)

    def "starts capturing output"() {
        def suite = new DefaultTestSuiteDescriptor("1", "Foo")
        def event = new TestStartEvent(1)

        when:
        processor.started(suite, event)

        then: 1 * target.started(suite, event)
        then: 1 * redirector.setOutputOwner("1")
        then: 1 * redirector.startRedirecting()
        0 * _
    }

    def "starts capturing only on first test"() {
        def test = new DefaultTestDescriptor("2", "Bar", "Baz")
        def testEvent = new TestStartEvent(2, "1")

        processor.started(new DefaultTestSuiteDescriptor("1", "Foo"), new TestStartEvent(1))

        when: processor.started(test, testEvent)

        then:
        1 * target.started(test, testEvent)
        1 * redirector.setOutputOwner("2")
        0 * _
    }

    def "when test completes its parent will be the owner of output"() {
        def test = new DefaultTestDescriptor("2", "Bar", "Baz")
        def testEvent = new TestStartEvent(2, "99")
        def complete = new TestCompleteEvent(1)

        processor.started(new DefaultTestSuiteDescriptor("1", "Foo"), new TestStartEvent(1))
        processor.started(test, testEvent)

        when: processor.completed("2", complete)

        then:
        1 * redirector.setOutputOwner("99")
        1 * target.completed("2", complete)
        0 * _
    }

    def "when test without parent completes the root suite be the owner of output"() {
        def test = new DefaultTestDescriptor("2", "Bar", "Baz")
        def testEvent = new TestStartEvent(2, null)
        def complete = new TestCompleteEvent(1)

        processor.started(new DefaultTestSuiteDescriptor("1", "Foo"), new TestStartEvent(1))
        processor.started(test, testEvent)

        when: processor.completed("2", complete)

        then:
        1 * redirector.setOutputOwner("1")
        1 * target.completed("2", complete)
        0 * _
    }

    def "stops redirecting when suite is completed"() {
        def test = new DefaultTestDescriptor("2", "Bar", "Baz")
        def testEvent = new TestStartEvent(2, null)
        def complete = new TestCompleteEvent(1)

        processor.started(new DefaultTestSuiteDescriptor("1", "Foo"), new TestStartEvent(1))
        processor.started(test, testEvent)
        processor.completed("2", complete)

        when: processor.completed("1", complete)

        then:
        1 * redirector.stopRedirecting()
        1 * target.completed("1", complete)
        0 * _
    }
}
