/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.testng

import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.id.IdGenerator
import org.gradle.internal.time.Clock
import org.testng.ITestClass
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class TestNGTestResultProcessorAdapterTest extends Specification {

    private static final Long TEST_CLASS_ID = 1L
    private TestResultProcessor resultProcessor = Mock()

    private IdGenerator<Long> idGenerator = Mock {
        generateId() >> TEST_CLASS_ID
    }

    @Subject
    private TestNGTestResultProcessorAdapter resultProcessorAdapter = new TestNGTestResultProcessorAdapter(
        resultProcessor, idGenerator, Mock(Clock))

    @Issue("https://github.com/gradle/gradle/issues/3545")
    def "runs onAfterClass hook only once per test class"() {
        given:
        def testClass = Stub(ITestClass) {
            getTestMethods() >> []
        }
        resultProcessorAdapter.onBeforeClass(testClass)

        when:
        resultProcessorAdapter.onAfterClass(testClass)

        then:
        1 * resultProcessor.completed(TEST_CLASS_ID, _ as TestCompleteEvent)

        when:
        resultProcessorAdapter.onAfterClass(testClass)

        then:
        0 * resultProcessor.completed(_, _ as TestCompleteEvent)
    }
}
