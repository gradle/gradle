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

import org.gradle.api.testing.TestClassProcessor
import org.gradle.api.testing.fabric.TestClassRunInfo
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TimeProvider
import org.jmock.Sequence
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
class WorkerTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestClassProcessor target = context.mock(TestClassProcessor.class)
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class)
    private final TestClassRunInfo runInfo = context.mock(TestClassRunInfo.class)
    private final TimeProvider timeProvider = context.mock(TimeProvider.class)
    private final Sequence sequence = context.sequence('seq')
    private final WorkerTestClassProcessor processor = new WorkerTestClassProcessor(target, 'worker-id', 'worker display name', timeProvider)

    @Test
    public void generatesAStartEventOnStartProcessing() {
        context.checking {
            one(timeProvider).getCurrentTime();
            will(returnValue(100L))

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo('worker-id'))
                assertThat(suite.name, equalTo('worker display name'))
                assertThat(event.startTime, equalTo(100L))
            }
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor)
    }

    @Test
    public void generatesACompleteEventOnEndProcessing() {
        context.checking {
            atLeast(1).of(timeProvider).getCurrentTime()
            will(returnValue(200L))

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.id, equalTo('worker-id'))
            }
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)

            one(target).endProcessing()
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo('worker-id')), withParam(notNullValue()))
            inSequence(sequence)
            will { id, TestCompleteEvent event ->
                assertThat(event.endTime, equalTo(200L))
                assertThat(event.resultType, nullValue())
                assertThat(event.failure, nullValue())
            }
        }

        processor.startProcessing(resultProcessor)
        processor.endProcessing()
    }

    @Test
    public void firesFailureEventWhenStartProcessingFails() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            ignoring(timeProvider)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)
            will(throwException(failure))

            one(resultProcessor).failure('worker-id', failure)
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor)
    }

    @Test
    public void completeEventContainsStopProcessingException() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            ignoring(timeProvider)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)

            one(target).endProcessing()
            inSequence(sequence)
            will(throwException(failure))

            one(resultProcessor).failure('worker-id', failure)
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo('worker-id')), withParam(notNullValue()))
            inSequence(sequence)
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
                assertThat(event.failure, nullValue())
            }
        }

        processor.startProcessing(resultProcessor)
        processor.endProcessing()
    }

    @Test
    public void generatesAFailedEventOnFailureToProcessTestClass() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            ignoring(timeProvider)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            inSequence(sequence)

            one(target).startProcessing(resultProcessor)
            inSequence(sequence)

            one(target).processTestClass(runInfo)
            inSequence(sequence)
            will(throwException(failure))

            one(resultProcessor).failure('worker-id', failure)
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor)
        processor.processTestClass(runInfo)
    }
}

