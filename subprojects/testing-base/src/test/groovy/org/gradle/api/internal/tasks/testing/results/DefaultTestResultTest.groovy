/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results;


import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestResult.ResultType
import spock.lang.Specification

public class DefaultTestResultTest extends Specification {

    def "construct itself from the state"() {
        expect:
        def state = new TestState(new DefaultTestDescriptor("12", "FooTest", "shouldWork"), new TestStartEvent(100L), new HashMap());
        state.completed(new TestCompleteEvent(200L, ResultType.SKIPPED))

        when:
        def result = new DefaultTestResult(state)

        then:
        result.getStartTime() == 100L
        result.getEndTime() == 200L
        result.resultType == ResultType.SKIPPED
        result.exceptions == state.failures.collect { it.rawFailure }
        result.testCount == state.testCount
        result.successfulTestCount == state.successfulCount
        result.failedTestCount == state.failedCount
    }
}
