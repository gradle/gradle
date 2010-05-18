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
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TimeProvider
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.TestCompleteEvent

@RunWith(JMock.class)
class TestMainActionTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestClassProcessor processor = context.mock(TestClassProcessor.class)
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class)
    private final Runnable detector = context.mock(Runnable.class)
    private final TimeProvider timeProvider = context.mock(TimeProvider.class)
    private final TestMainAction action = new TestMainAction(detector, processor, resultProcessor, timeProvider)

    @Test
    public void firesStartAndEndEventsAroundDetectorExecution() {
        context.checking {
            one(timeProvider).getCurrentTime()
            will(returnValue(100L))
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo('root'))
                assertThat(event.startTime, equalTo(100L))
            }
            one(processor).startProcessing(withParam(notNullValue()))
            one(detector).run()
            one(processor).stop()
            one(timeProvider).getCurrentTime()
            will(returnValue(200L))
            one(resultProcessor).completed(withParam(equalTo('root')), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.endTime, equalTo(200L))
                assertThat(event.resultType, nullValue())
            }
        }

        action.run();
    }

    @Test
    public void firesEndEventsWhenDetectorFails() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            ignoring(timeProvider).getCurrentTime()
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            one(processor).startProcessing(withParam(notNullValue()))
            one(detector).run()
            will(throwException(failure))
            one(processor).stop()
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
        }

        try {
            action.run()
            fail()
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }

    @Test
    public void firesEndEventsWhenStartProcessingFails() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            ignoring(timeProvider).getCurrentTime()
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            one(processor).startProcessing(withParam(notNullValue()))
            will(throwException(failure))
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
        }

        try {
            action.run()
            fail()
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }

    @Test
    public void firesEndEventsWhenEndProcessingFails() {
        RuntimeException failure = new RuntimeException()

        context.checking {
            ignoring(timeProvider).getCurrentTime()
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            one(processor).startProcessing(withParam(notNullValue()))
            one(detector).run()
            one(processor).stop()
            will(throwException(failure))
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
        }

        try {
            action.run()
            fail()
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure))
        }
    }
}
