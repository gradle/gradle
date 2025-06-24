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

import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.time.MockClock
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

class TestMainActionTest extends Specification {
    private static final long CLOCK_START = 100L
    private static final long CLOCK_INCREMENT = MockClock.DEFAULT_AUTOINCREMENT_MS

    private final TestClassProcessor processor = Mock()
    private final TestResultProcessor resultProcessor = Mock()
    private final Runnable detector = Mock()
    private final def timeProvider = MockClock.createAutoIncrementingAt(CLOCK_START)
    private final WorkerLeaseRegistry.WorkerLease lease = Mock()
    private final WorkerLeaseService workerLeaseService = Mock()
    private final TestMainAction action = new TestMainAction(detector, processor, resultProcessor, workerLeaseService, timeProvider, "rootTestSuiteId456", "Test Run")

    def 'fires start and end events around detector execution'() {
        when:
        action.run()

        then:
        1 * resultProcessor.started({ it.id == 'rootTestSuiteId456' }, { it.startTime == CLOCK_START })
        then:
        1 * processor.startProcessing(!null)
        then:
        1* detector.run()
        then:
        1 * workerLeaseService.blocking(_) >> { Runnable runnable -> runnable.run() }
        1 * processor.stop()
        then:
        1 * resultProcessor.completed({ it == "rootTestSuiteId456" }, { event ->
            event.endTime == CLOCK_START + CLOCK_INCREMENT && event.resultType == null
        })
        0 * _._
    }

    def 'fires end events when detector fails'() {
        given:
        def failure = new RuntimeException()

        when:
        action.run()

        then:
        1 * resultProcessor.started(!null, !null)
        then:
        1 * processor.startProcessing(!null)
        then:
        1 * detector.run() >> { throw failure }
        then:
        1 * workerLeaseService.blocking(_) >> { Runnable runnable -> runnable.run() }
        1 * processor.stop()
        then:
        1 * resultProcessor.completed(!null, !null)

        0 * resultProcessor._
        0 * detector._
        0 * processor._

        def exception = thrown(RuntimeException)
        exception == failure
    }


    def 'fires end events when start processing fails'() {
        given:
        def failure = new RuntimeException()

        when:
        action.run()

        then:
        1 * resultProcessor.started(!null, !null)
        then:
        1 * processor.startProcessing(!null) >> { throw failure }
        then:
        1 * resultProcessor.completed(!null, !null)

        0 * resultProcessor._
        0 * detector._
        0 * processor._

        def exception = thrown(RuntimeException)
        exception == failure
    }

    def 'fires end events when end processing fails'() {
        given:
        def failure = new RuntimeException()

        when:
        action.run()

        then:
        1 * resultProcessor.started(!null, !null)
        then:
        1 * processor.startProcessing(!null)
        then:
        1 * detector.run()
        then:
        1 * workerLeaseService.blocking(_) >> { Runnable runnable -> runnable.run() }
        1 * processor.stop() >> { throw failure }
        then:
        1 * resultProcessor.completed(!null, !null)

        0 * resultProcessor._
        0 * detector._
        0 * processor._

        def exception = thrown(RuntimeException)
        exception == failure
    }
}
