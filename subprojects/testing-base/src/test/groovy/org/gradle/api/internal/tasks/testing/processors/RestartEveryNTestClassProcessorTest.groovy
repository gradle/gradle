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
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.Factory
import spock.lang.Specification

class RestartEveryNTestClassProcessorTest extends Specification {
    private final Factory<TestClassProcessor> factory = Mock();
    private final TestClassProcessor delegate = Mock();
    private final TestClassRunInfo test1 = Mock();
    private final TestClassRunInfo test2 = Mock();
    private final TestClassRunInfo test3 = Mock();
    private final TestResultProcessor resultProcessor = Mock();
    private RestartEveryNTestClassProcessor processor = new RestartEveryNTestClassProcessor(factory, 2);

    def 'creates delegate processor on first test'() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)

        then:
        1 * factory.create() >> delegate
        then:
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        0 * _._
    }

    def 'ends processing on delegate processor on nth test'() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.processTestClass(test2)

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.processTestClass(test2)
        then:
        1 * delegate.stop()
        0 * _._
    }

    def 'creates new delegate processor on (n + 1)th test'() {
        given:
        TestClassProcessor delegate2 = Mock()

        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.processTestClass(test2)

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.processTestClass(test2)
        then:
        1 * delegate.stop()
        0 * _._

        when:
        processor.processTestClass(test3)

        then:
        1 * factory.create() >> delegate2
        then:
        1 * delegate2.startProcessing(resultProcessor)
        then:
        1 * delegate2.processTestClass(test3)
        0 * _._
    }

    def 'processing on delegate processor ends on end of processing'() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.stop()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.stop()
        0 * _._
    }

    def 'does nothing on end of processing when no tests received'() {
        expect:
        processor.stop()
    }

    def 'does nothing on end of processing when on nth test'() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.processTestClass(test2)
        processor.stop()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.processTestClass(test2)
        then:
        1 * delegate.stop()
        0 * _._
    }

    def 'uses single batch when n equals zero'() {
        given:
        processor = new RestartEveryNTestClassProcessor(factory, 0)

        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.processTestClass(test2)
        processor.stop()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.processTestClass(test2)
        then:
        1 * delegate.stop()
        0 * _._
    }

    def "stopNow propagates to factory created processors"() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.stopNow()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.stopNow()
        0 * _._
    }

    def "stopNow does not propagate when no processor"() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.processTestClass(test2)
        processor.stopNow()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        then:
        1 * delegate.processTestClass(test2)
        then:
        1 * delegate.stop()
        0 * _._
    }

    def "stopNow does nothing after stop completed"() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.stop()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        1 * delegate.processTestClass(test1)
        1 * delegate.stop()

        when:
        processor.stopNow()
        then:
        0 * _._
    }

    def "processTestClass has no effect after stopNow"() {
        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test1)
        processor.stopNow()

        then:
        1 * factory.create() >> delegate
        1 * delegate.startProcessing(resultProcessor)
        then:
        1 * delegate.processTestClass(test1)
        1 * delegate.stopNow()

        when:
        processor.processTestClass(test2)

        then:
        0 * _._
    }
}
