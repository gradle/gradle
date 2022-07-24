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


package org.gradle.api.internal.tasks.testing.results

import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import spock.lang.Specification

class AttachParentTestResultProcessorTest extends Specification {
    private final TestResultProcessor target = Mock()
    private final AttachParentTestResultProcessor processor = new AttachParentTestResultProcessor(target)

    def attachesTestToCurrentlyExecutingRootSuite() {
        TestDescriptorInternal suite = suite('suite')
        TestDescriptorInternal test = test('test')
        TestStartEvent suiteStartEvent = new TestStartEvent(100L)

        when:
        processor.started(suite, suiteStartEvent)
        processor.started(test, new TestStartEvent(200L))

        then:
        1 * target.started(suite, suiteStartEvent)
        1 * target.started(test, { it.parentId == 'suite' })
    }

    def canHaveMoreThanOneRootSuite() {
        TestDescriptorInternal root = suite('root')
        TestDescriptorInternal other = suite('suite1')
        TestDescriptorInternal test = test('test')

        processor.started(root, new TestStartEvent(100L))
        processor.completed('root', new TestCompleteEvent(200L))
        processor.started(other, new TestStartEvent(200L))

        when:
        processor.started(test, new TestStartEvent(200L))

        then:
        1 * target.started(test, { it.parentId == 'suite1' })
    }

    def doesNothingToTestWhichHasAParentId() {
        TestDescriptorInternal suite = suite('suite')
        TestDescriptorInternal test = test('test')
        TestStartEvent testStartEvent = new TestStartEvent(200L, 'parent')
        processor.started(suite, new TestStartEvent(100L))

        when:
        processor.started(test, testStartEvent)

        then:
        1 * target.started(test, testStartEvent)
    }

    TestDescriptorInternal test(String id) {
        [isComposite: {false}, getId: {id}, toString: {id}] as TestDescriptorInternal
    }

    TestDescriptorInternal suite(String id) {
        [isComposite: {true}, getId: {id}, toString: {id}] as TestDescriptorInternal
    }
}
