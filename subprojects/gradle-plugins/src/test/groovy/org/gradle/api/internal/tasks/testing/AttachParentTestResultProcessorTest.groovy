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


import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.integration.junit4.JMock
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JMock.class)
class AttachParentTestResultProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final TestResultProcessor target = context.mock(TestResultProcessor.class)
    private final AttachParentTestResultProcessor processor = new AttachParentTestResultProcessor(target)

    @Test
    public void attachesTestToCurrentlyExecutingSuite() {
        TestInternal suite = suite('suite')
        TestInternal test = test('test')
        TestStartEvent testStartEvent = new TestStartEvent(200L)

        context.checking {
            ignoring(target)
        }

        processor.started(suite, new TestStartEvent(100L))
        processor.started(test, testStartEvent)

        assertThat(testStartEvent.parentId, equalTo('suite'))
    }

    @Test
    public void attachesSuiteToMostCurrentlyExecutingSuite() {
        TestInternal parent = suite('suite')
        TestInternal child = suite('test')
        TestStartEvent childStartEvent = new TestStartEvent(200L)

        context.checking {
            ignoring(target)
        }

        processor.started(parent, new TestStartEvent(100L))
        processor.started(child, childStartEvent)

        assertThat(childStartEvent.parentId, equalTo('suite'))
    }

    @Test
    public void popsSuiteOffStackWhenComplete() {
        TestInternal root = suite('root')
        TestInternal other = suite('suite1')
        TestInternal test = test('test')
        TestStartEvent testStartEvent = new TestStartEvent(200L)

        context.checking {
            ignoring(target)
        }

        processor.started(root, new TestStartEvent(100L))
        processor.started(other, new TestStartEvent(100L))
        processor.completed('suite1', new TestCompleteEvent(200L))
        processor.started(test, testStartEvent)

        assertThat(testStartEvent.parentId, equalTo('root'))
    }

    @Test
    public void doesNothingToTestWhichHasAParentId() {
        TestInternal suite = suite('suite')
        TestInternal test = test('test')
        TestStartEvent testStartEvent = new TestStartEvent(200L, 'parent')

        context.checking {
            ignoring(target)
        }

        processor.started(suite, new TestStartEvent(100L))
        processor.started(test, testStartEvent)

        assertThat(testStartEvent.parentId, equalTo('parent'))
    }

    @Test
    public void doesNothingToSuiteWhenNoSuiteExecuting() {
        TestInternal suite = suite('suite')
        TestStartEvent suiteStartEvent = new TestStartEvent(100L)

        context.checking {
            ignoring(target)
        }

        processor.started(suite, suiteStartEvent)

        assertThat(suiteStartEvent.parentId, nullValue())
    }

    @Test
    public void doesNothingToTestWhenNoSuiteExecuting() {
        TestInternal test = test('test')
        TestStartEvent testStartEvent = new TestStartEvent(200L)

        context.checking {
            ignoring(target)
        }

        processor.started(test, testStartEvent)

        assertThat(testStartEvent.parentId, nullValue())
    }

    TestInternal test(String id) {
        [isComposite: {false}, getId: {id}] as TestInternal
    }

    TestInternal suite(String id) {
        [isComposite: {true}, getId: {id}] as TestInternal
    }
}
