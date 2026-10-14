/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results

import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDiscoveryEvent
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.WorkItemProgressEvent
import spock.lang.Specification

class ProgressReportingTestResultProcessorTest extends Specification {
    def delegate = Mock(TestResultProcessor)
    def events = []
    def processor = new ProgressReportingTestResultProcessor(delegate, { WorkItemProgressEvent event -> events << "${event.kind} ${event.count}".toString() } as OutputEventListener)

    def "reports discovered tests up front, then starts and completes them as they run"() {
        when:
        processor.discovered(new TestDiscoveryEvent(2))
        processor.started(suite("suite"), new TestStartEvent(0))
        run("test1")
        run("test2")
        processor.completed("suite", new TestCompleteEvent(0))
        processor.settle()

        then:
        events == ["SUBMITTED 2", "STARTED 1", "SUCCEEDED 1", "STARTED 1", "SUCCEEDED 1"]
    }

    def "reports tests that start without being discovered"() {
        when:
        processor.discovered(new TestDiscoveryEvent(1))
        run("test1")
        run("test2")
        processor.settle()

        then:
        events == ["SUBMITTED 1", "STARTED 1", "SUCCEEDED 1", "SUBMITTED 1", "STARTED 1", "SUCCEEDED 1"]
    }

    def "reports failed tests as failed and assumption failures as succeeded"() {
        when:
        run("failed", TestFailure.fromTestFrameworkFailure(new RuntimeException()))
        run("skipped", DefaultTestFailure.fromTestAssumptionFailure(new RuntimeException()))

        then:
        events == ["SUBMITTED 1", "STARTED 1", "FAILED 1", "SUBMITTED 1", "STARTED 1", "SUCCEEDED 1"]
    }

    def "settles discovered tests that never complete"() {
        when:
        processor.discovered(new TestDiscoveryEvent(3))
        processor.started(test("test1"), new TestStartEvent(0))
        processor.settle()

        then:
        events == ["SUBMITTED 3", "STARTED 1", "SUCCEEDED 3"]
    }

    def "forwards all events to the delegate"() {
        def discovery = new TestDiscoveryEvent(1)
        def test = test("test1")
        def failure = TestFailure.fromTestFrameworkFailure(new RuntimeException())

        when:
        processor.discovered(discovery)
        processor.started(test, new TestStartEvent(0))
        processor.failure("test1", failure)
        processor.completed("test1", new TestCompleteEvent(0))

        then:
        1 * delegate.discovered(discovery)
        1 * delegate.started(test, _)
        1 * delegate.failure("test1", failure)
        1 * delegate.completed("test1", _)
    }

    private void run(String id, TestFailure failure = null) {
        processor.started(test(id), new TestStartEvent(0))
        if (failure != null) {
            processor.failure(id, failure)
        }
        processor.completed(id, new TestCompleteEvent(0))
    }

    private static DefaultTestDescriptor test(String id) {
        new DefaultTestDescriptor(id, "Foo", id)
    }

    private static DefaultTestSuiteDescriptor suite(String id) {
        new DefaultTestSuiteDescriptor(id, id)
    }
}
