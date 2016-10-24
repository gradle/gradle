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

package org.gradle.api.internal.tasks.testing

import spock.lang.Specification
import org.gradle.internal.time.TimeProvider
import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor

class SuiteTestClassProcessorTest extends Specification {
    private final TestResultProcessor resultProcessor = Mock()
    private final TestClassProcessor targetProcessor = Mock()
    private final TestDescriptorInternal suiteDescriptor = Mock()
    private final TestClassRunInfo testClass = Mock()
    private final TimeProvider timeProvider = Mock()
    private final SuiteTestClassProcessor processor = new SuiteTestClassProcessor(suiteDescriptor, targetProcessor, timeProvider)

    def setup() {
        _ * suiteDescriptor.getId() >> 'id'
        _ * suiteDescriptor.toString() >> '<suite>'
        _ * suiteDescriptor.isComposite() >> true
        _ * testClass.getTestClassName() >> '<class-name>'
    }

    def firesSuiteStartEventOnStartProcessing() {
        when:
        processor.startProcessing(resultProcessor)

        then:
        1 * resultProcessor.started(suiteDescriptor, !null)
        1 * targetProcessor.startProcessing(!null) >> { args ->
            def processor = args[0]
            processor instanceof AttachParentTestResultProcessor && args[0].processor == resultProcessor
        }
    }

    def firesSuiteCompleteEventOnEndProcessing() {
        processor.startProcessing(resultProcessor)

        when:
        processor.stop()

        then:
        1 * resultProcessor.completed('id', !null)
        1 * targetProcessor.stop()
    }

    def firesAFailureEventOnStartProcessingFailure() {
        RuntimeException failure = new RuntimeException()
        processor.startProcessing(resultProcessor)

        when:
        processor.startProcessing(resultProcessor)

        then:
        1 * resultProcessor.started(suiteDescriptor, !null)
        1 * targetProcessor.startProcessing(!null) >> { throw failure }
        1 * resultProcessor.failure('id', !null) >> { args ->
            def e = args[1]
            assert e instanceof TestSuiteExecutionException
            assert e.message == 'Could not start <suite>.'
            assert e.cause == failure
        }
    }

    def firesAFailureEventOnTestClassProcessingFailure() {
        RuntimeException failure = new RuntimeException()
        processor.startProcessing(resultProcessor)

        when:
        processor.processTestClass(testClass)

        then:
        1 * targetProcessor.processTestClass(testClass) >> { throw failure }
        1 * resultProcessor.failure('id', !null) >> { args ->
            def e = args[1]
            assert e instanceof TestSuiteExecutionException
            assert e.message == 'Could not execute test class \'<class-name>\'.'
            assert e.cause == failure
        }
    }

    def firesAFailureEventOnCompleteProcessingFailure() {
        RuntimeException failure = new RuntimeException()
        processor.startProcessing(resultProcessor)

        when:
        processor.stop()

        then:
        1 * targetProcessor.stop() >> { throw failure }
        1 * resultProcessor.failure('id', !null) >> { args ->
            def e = args[1]
            assert e instanceof TestSuiteExecutionException
            assert e.message == 'Could not complete execution for <suite>.'
            assert e.cause == failure
        }
        1 * resultProcessor.completed('id', !null)
    }
}


